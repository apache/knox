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

"""PathAclsAuthz must apply every overlapping rule, whatever order they are held in.

The pathacls-order topology pairs one broad allow-everyone rule with 31 restrictive
rules. If the provider applied only the first matching rule, each restricted path
would be open whenever the broad rule happened to come first, so at least one of
them would almost always be reachable by guest.
"""

import unittest

from common_utils import basic_auth_get, gateway_base_url

RESTRICTED_PATHS = ["v1/gateway-status"] + [f"v1/restricted-{i:02d}" for i in range(1, 31)]


class TestPathAclsRuleOrder(unittest.TestCase):
    """Overlapping rules must all grant; a broad allow rule must never win on its own."""

    def setUp(self):
        self.topology_url = gateway_base_url() + "gateway/pathacls-order/"

    def test_unrestricted_path_is_allowed(self):
        """Positive control: guest passes the broad rule on an unrestricted path."""
        response = basic_auth_get(self.topology_url + "v1/ping", "guest", "guest-password")
        self.assertEqual(response.status_code, 200, msg=response.text)

    def test_restricted_paths_are_allowed_for_admin(self):
        """Positive control: admin passes both rules on every restricted path."""
        response = basic_auth_get(
            self.topology_url + "v1/gateway-status", "admin", "admin-password"
        )
        self.assertEqual(response.status_code, 200, msg=response.text)
        for path in RESTRICTED_PATHS[1:]:
            with self.subTest(path=path):
                response = basic_auth_get(self.topology_url + path, "admin", "admin-password")
                self.assertNotEqual(response.status_code, 403, msg=response.text)

    def test_restricted_paths_are_denied_for_guest(self):
        """Guest is denied on every restricted path, regardless of rule order."""
        reachable = []
        for path in RESTRICTED_PATHS:
            response = basic_auth_get(self.topology_url + path, "guest", "guest-password")
            if response.status_code != 403:
                reachable.append(f"{path} -> {response.status_code}")
        self.assertEqual(
            reachable,
            [],
            msg="guest got past PathAclsAuthz on restricted paths (broad rule applied "
            "instead of the restrictive one): " + ", ".join(reachable),
        )


if __name__ == "__main__":
    unittest.main()
