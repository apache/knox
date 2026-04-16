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
import unittest

from requests.auth import HTTPBasicAuth

from common_utils import (
    collect_actor_group_values,
    gateway_base_url,
    knox_get,
    knox_ldap_admin_auth,
    knox_ldap_guest_auth,
)


class TestRemoteAuthExtAuthzAdditionalPath(unittest.TestCase):
    def setUp(self):
        self.base_url = gateway_base_url()
        self.extauthz_url = self.base_url + "gateway/remoteauth/auth/api/v1/extauthz"

    def test_extauthz_success(self):
        response = knox_get(
            self.extauthz_url,
            auth=knox_ldap_guest_auth(),
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("X-Knox-Actor-ID", response.headers)
        self.assertEqual(response.headers["X-Knox-Actor-ID"], "guest")

    def test_extauthz_additional_path_is_ignored(self):
        response = knox_get(
            self.extauthz_url + "/some/extra/path",
            auth=knox_ldap_guest_auth(),
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("X-Knox-Actor-ID", response.headers)
        self.assertEqual(response.headers["X-Knox-Actor-ID"], "guest")

    def test_extauthz_admin_user_actor_and_groups(self):
        """Admin via remoteauth extauthz maps LDAP groups onto X-Knox-Actor-* headers."""
        response = knox_get(self.extauthz_url, auth=knox_ldap_admin_auth())
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers.get("X-Knox-Actor-ID"), "admin")
        groups = collect_actor_group_values(response, prefix="X-Knox-Actor-Groups")
        for name in ("longGroupName1", "longGroupName2"):
            self.assertIn(name, groups)

    def test_extauthz_additional_deep_path_still_ignored(self):
        """ignore.additional.path accepts longer arbitrary suffix segments."""
        response = knox_get(
            self.extauthz_url + "/a/b/c/d/extra",
            auth=knox_ldap_guest_auth(),
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers.get("X-Knox-Actor-ID"), "guest")

    def test_extauthz_bad_credentials_unauthorized(self):
        response = knox_get(
            self.extauthz_url,
            auth=HTTPBasicAuth("baduser", "badpass"),
        )
        self.assertEqual(response.status_code, 401)

    def test_extauthz_missing_credentials_server_error(self):
        # No Authorization header: RemoteAuth hits an error path (500), not 401.
        response = knox_get(self.extauthz_url)
        self.assertEqual(response.status_code, 500)


if __name__ == "__main__":
    unittest.main()

