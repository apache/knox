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

"""Integration tests for the LDAP backend password alias lookup retry (KNOX-3513).

The demo LDAP backend's system (bind) password is configured as
${ALIAS=gateway_ldap_demoldap_system_password}, and gateway-site.xml enables
gateway.ldap.backend.password.alias.retry.attempts. gateway.sh creates that alias
only after Knox has failed to find it and logged a retry, reproducing a secret
that is persisted after the embedded LDAP service has loaded its configuration.

These tests check the gateway log of the latest gateway run: the lookup must have
been retried, and Knox must not have given up on the alias. That the proxy then
binds to the demo LDAP with the resolved password is proven by
test_knox_ldap_proxy_search.py.
"""

from __future__ import annotations

import os

import pytest

ALIAS = "gateway_ldap_demoldap_system_password"

# The Knox logs directory, mounted read-only into the tests container.
GATEWAY_LOG = os.path.join(os.environ.get("KNOX_LOGS_DIR", "/knox-logs"), "gateway.log")

# Logged once per gateway JVM start, before any service is initialized.
GATEWAY_START_MARKER = "System Property: java.home="

RETRY_MESSAGE = f"No value found yet in the gateway credential store for alias {ALIAS}"
GIVE_UP_MESSAGE = f"No value found in the gateway credential store for alias {ALIAS}"


def latest_gateway_run_log_lines() -> list[str]:
    """Return the gateway.log lines written since the latest gateway start."""
    if not os.path.isfile(GATEWAY_LOG):
        pytest.skip(f"{GATEWAY_LOG} is not available (not running in the docker compose stack)")
    with open(GATEWAY_LOG, encoding="utf-8", errors="replace") as log_file:
        lines = log_file.readlines()
    start = 0
    for index, line in enumerate(lines):
        if GATEWAY_START_MARKER in line:
            start = index
    return lines[start:]


def test_ldap_backend_password_alias_lookup_is_retried() -> None:
    """Knox retried the lookup of the alias that was missing when the LDAP service started."""
    retries = [line for line in latest_gateway_run_log_lines() if RETRY_MESSAGE in line]
    assert retries, f"Expected Knox to log a retry for alias {ALIAS} in {GATEWAY_LOG}"


def test_ldap_backend_password_alias_is_resolved_before_retries_are_exhausted() -> None:
    """Knox resolved the alias once it was created instead of giving up on it."""
    give_ups = [line for line in latest_gateway_run_log_lines() if GIVE_UP_MESSAGE in line]
    assert not give_ups, f"Knox gave up resolving alias {ALIAS}: {give_ups}"
