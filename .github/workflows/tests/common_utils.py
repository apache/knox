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

from __future__ import annotations

import os
import unittest
from typing import Any

import requests
import urllib3

# Default timeout for HTTP calls to the gateway (self-signed TLS, CI).
KNOX_REQUEST_TIMEOUT = 30

HSTS_HEADER_NAME = "Strict-Transport-Security"
HSTS_EXPECTED_VALUE = "max-age=300; includeSubDomains"

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def gateway_base_url() -> str:
    """Return KNOX_GATEWAY_URL with a trailing slash."""
    url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
    return url if url.endswith("/") else (url + "/")


def knox_get(url: str, **kwargs: Any) -> requests.Response:
    """GET against Knox with verify=False and default timeout unless overridden."""
    opts: dict[str, Any] = {"verify": False, "timeout": KNOX_REQUEST_TIMEOUT}
    opts.update(kwargs)
    return requests.get(url, **opts)


def knox_post(url: str, **kwargs: Any) -> requests.Response:
    """POST against Knox with verify=False and default timeout unless overridden."""
    opts: dict[str, Any] = {"verify": False, "timeout": KNOX_REQUEST_TIMEOUT}
    opts.update(kwargs)
    return requests.post(url, **opts)


def collect_actor_group_values(
    response: requests.Response, prefix: str = "x-knox-actor-groups"
) -> list[str]:
    """Comma-split values from all response headers whose names start with prefix (case-insensitive)."""
    prefix_lower = prefix.lower()
    all_groups: list[str] = []
    for name in response.headers:
        if name.lower().startswith(prefix_lower):
            all_groups.extend(response.headers[name].split(","))
    return all_groups


def assert_hsts_header(testcase: unittest.TestCase, response: requests.Response) -> None:
    testcase.assertIn(HSTS_HEADER_NAME, response.headers)
    testcase.assertEqual(response.headers[HSTS_HEADER_NAME], HSTS_EXPECTED_VALUE)
