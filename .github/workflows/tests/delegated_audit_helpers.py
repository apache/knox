# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Helpers for asserting the audit trail of delegated (act-claim) token use (KNOX-3568).

Tokens are presented at knoxauthtoken's /auth/api/v1/pre with a random audit_marker query
parameter, which leads to the request id of the records that request left in gateway-audit.log.
"""

from __future__ import annotations

import os
import re
import time
import uuid
from dataclasses import dataclass

import requests

from common_utils import gateway_base_url, knox_get

DESTINATION_PATH = "/gateway/knoxauthtoken/auth/api/v1/pre"
SERVER_NAME_HEADER = "x-dest-server-name"

AUDIT_LOG = os.path.join(os.environ.get("KNOX_LOGS_DIR", "/knox-logs"), "gateway-audit.log")
AUDIT_WAIT_SEC = 10.0

# <date> <root id>|<parent id>|<request id>|<logger>|<ip>|<service>|<user>|<proxy user>|
# <system user>|<action>|<resource type>|<resource name>|<outcome>|<message>
_AUDIT_COLUMNS = 14
_FIELD = re.compile(r'(?:^|(?<=\s))([a-z_]+)=("(?:[^"\\]|\\.)*"|\S*)')
_ESCAPE = re.compile(r"\\(.)")


@dataclass(frozen=True)
class AuditRecord:
    """One gateway-audit.log line."""

    line: str
    request_id: str
    user: str
    action: str
    resource: str
    outcome: str
    message: str

    @property
    def fields(self) -> dict[str, str]:
        """The message's name=value fields, with quoted values unquoted."""
        fields = {}
        for name, value in _FIELD.findall(self.message):
            if value.startswith('"'):
                value = _ESCAPE.sub(r"\1", value[1:-1])
            fields[name] = value
        return fields


def _parse(line: str) -> AuditRecord | None:
    columns = line.rstrip("\n").split("|", _AUDIT_COLUMNS - 1)
    if len(columns) < _AUDIT_COLUMNS:
        return None
    return AuditRecord(line=line, request_id=columns[2], user=columns[6], action=columns[9],
                       resource=columns[11], outcome=columns[12], message=columns[13])


def read_audit_log(testcase) -> list[str]:
    """Return every line of gateway-audit.log, skipping the test outside the compose stack."""
    if not os.path.isfile(AUDIT_LOG):
        testcase.skipTest(f"{AUDIT_LOG} is not available (not running in the docker compose stack)")
    with open(AUDIT_LOG, encoding="utf-8", errors="replace") as audit_log:
        return audit_log.readlines()


def call_destination(token: str, destination_service: str) -> tuple[requests.Response, str]:
    """Present ``token`` for ``destination_service``; returns the response and request URI."""
    uri = f"{DESTINATION_PATH}?audit_marker={uuid.uuid4()}"
    response = knox_get(
        gateway_base_url() + uri.lstrip("/"),
        headers={"Authorization": f"Bearer {token}", SERVER_NAME_HEADER: destination_service},
    )
    return response, uri


def audit_records_for(testcase, uri: str) -> list[AuditRecord]:
    """Return every record of the request to ``uri``, once its "Response status" record is in.

    Only some of the records carry the query string, so they are matched by request id.
    """
    deadline = time.monotonic() + AUDIT_WAIT_SEC
    while True:
        all_records = [record for record in map(_parse, read_audit_log(testcase))
                       if record is not None]
        request_ids = {record.request_id for record in all_records if record.resource == uri}
        records = [record for record in all_records if record.request_id in request_ids]
        completed = any(record.action == "access" and record.message.startswith("Response status")
                        for record in records)
        if completed or time.monotonic() >= deadline:
            return records
        time.sleep(0.25)


def records_with_action(records: list[AuditRecord], action: str) -> list[AuditRecord]:
    """The records whose action column is ``action``."""
    return [record for record in records if record.action == action]


def assert_token_not_in_audit_log(testcase, token: str) -> None:
    """Assert that no audit line carries the token's signature."""
    signature = token.rsplit(".", 1)[-1]
    for line in read_audit_log(testcase):
        testcase.assertNotIn(signature, line, "the audit log must not carry token material")
