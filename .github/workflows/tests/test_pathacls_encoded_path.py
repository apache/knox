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

"""PathAclsAuthz evaluates equivalent encodings of a path consistently.

Requests are sent with the path exactly as written, without client-side normalization.
"""

import unittest

import requests
from requests.auth import HTTPBasicAuth

from common_utils import KNOX_REQUEST_TIMEOUT, gateway_base_url

GUEST = ("guest", "guest-password")
ADMIN = ("admin", "admin-password")

EQUIVALENT_STATUS_PATHS = {
    "v1/%67ateway-status": 404,
    "v1/%67%61%74%65%77%61%79-status": 404,
    "v1/gateway%2Dstatus": 404,
    "v1/gateway-status;x=1": 200,
    "v1/gateway-status;": 200,
    "v1;x=1/gateway-status": 200,
    "v1/ping/%2e%2e/gateway-status": 404,
    "v1/ping/%2E%2E/gateway-status": 404,
    "v1/%2e/gateway-status": 404,
}


def raw_get(url, credentials):
    """GET url without letting requests re-encode or normalize the path."""
    session = requests.Session()
    prepared = session.prepare_request(
        requests.Request("GET", url, auth=HTTPBasicAuth(*credentials))
    )
    prepared.url = url
    return session.send(prepared, verify=False, timeout=KNOX_REQUEST_TIMEOUT)


class TestPathAclsEncodedPath(unittest.TestCase):
    """Equivalent forms of a restricted path get the same decision as the path itself."""

    def setUp(self):
        self.topology_url = gateway_base_url() + "gateway/pathacls-encoding/"

    def test_unrestricted_path_is_allowed(self):
        """Positive control: guest passes the broad rule on an unrestricted path."""
        response = raw_get(self.topology_url + "v1/ping", GUEST)
        self.assertEqual(response.status_code, 200, msg=response.text)

    def test_restricted_path_is_allowed_for_admin(self):
        """Positive control: admin reaches the restricted path."""
        response = raw_get(self.topology_url + "v1/gateway-status", ADMIN)
        self.assertEqual(response.status_code, 200, msg=response.text)

    def test_restricted_path_is_denied_for_guest(self):
        """Guest is denied on the plain restricted path."""
        response = raw_get(self.topology_url + "v1/gateway-status", GUEST)
        self.assertEqual(response.status_code, 403, msg=response.text)

    def test_rule_with_name_in_the_middle_of_the_path(self):
        """A rule naming a segment in the middle of the path applies on a live gateway."""
        for path in ("v1/restricted/x", "v1/restricted/x/y"):
            with self.subTest(path=path):
                self.assertEqual(raw_get(self.topology_url + path, GUEST).status_code, 403)
                self.assertEqual(raw_get(self.topology_url + path, ADMIN).status_code, 404)

    def test_equivalent_restricted_path_is_denied_for_guest(self):
        """Guest is denied on every equivalent form of the restricted path."""
        for path in EQUIVALENT_STATUS_PATHS:
            with self.subTest(path=path):
                response = raw_get(self.topology_url + path, GUEST)
                self.assertEqual(
                    response.status_code,
                    403,
                    msg=f"guest should be denied on {path!r}; got "
                    f"{response.status_code}: {response.text}",
                )

    def test_equivalent_restricted_path_is_not_denied_for_admin(self):
        """Admin passes PathAclsAuthz on every equivalent form; the HEALTH service then decides.

        Matrix-parameter forms reach gateway-status (200); percent-encoded and
        dot-segment forms have no HEALTH route (404). Neither is a 403.
        """
        for path, expected_status in EQUIVALENT_STATUS_PATHS.items():
            with self.subTest(path=path):
                response = raw_get(self.topology_url + path, ADMIN)
                self.assertEqual(response.status_code, expected_status, msg=response.text)


if __name__ == "__main__":
    unittest.main()
