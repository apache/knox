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

from common_utils import collect_actor_group_values, gateway_base_url, knox_get, knox_post


class TestKnoxAuthServicePreAuthAndPaths(unittest.TestCase):
    """KnoxLDAP auth service: preauth behavior and extauthz path handling."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.preauth_url = self.base_url + "gateway/knoxldap/auth/api/v1/pre"
        self.extauthz_url = self.base_url + "gateway/knoxldap/auth/api/v1/extauthz"

    def test_preauth_requires_auth(self):
        """Preauth without credentials must return 401."""
        response = knox_get(self.preauth_url)
        self.assertEqual(response.status_code, 401)

    def test_preauth_bad_credentials_unauthorized(self):
        """Invalid Basic auth must return 401."""
        response = knox_get(
            self.preauth_url,
            auth=HTTPBasicAuth("baduser", "badpass"),
        )
        self.assertEqual(response.status_code, 401)

    def test_preauth_post_supported(self):
        """POST preauth with guest credentials returns 200 and x-knox-actor-username."""
        response = knox_post(
            self.preauth_url,
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(response.status_code, 200)

        actor_id_header = "x-knox-actor-username"
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], "guest")

    def test_preauth_get_with_guest_credentials(self):
        """GET preauth with guest returns 200 and actor username guest."""
        response = knox_get(
            self.preauth_url,
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers.get("x-knox-actor-username"), "guest")

    def test_preauth_get_with_admin_includes_mapped_groups(self):
        """GET preauth with admin returns mapped LDAP groups in x-knox-actor-groups* headers."""
        response = knox_get(
            self.preauth_url,
            auth=HTTPBasicAuth("admin", "admin-password"),
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers.get("x-knox-actor-username"), "admin")
        groups = collect_actor_group_values(response, prefix="x-knox-actor-groups")
        for name in ("longGroupName1", "longGroupName2"):
            self.assertIn(name, groups)

    def test_extauthz_additional_path_not_ignored_in_knoxldap(self):
        """Unknown path under extauthz returns 404; extra segments are not ignored as success."""
        response = knox_get(
            self.extauthz_url + "/does-not-exist",
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()

