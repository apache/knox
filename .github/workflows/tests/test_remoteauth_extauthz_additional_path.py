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

"""Integration tests for RemoteAuth extauthz path handling."""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get


class TestRemoteAuthExtAuthzAdditionalPath(unittest.TestCase):
    """Verify RemoteAuth extauthz success, path handling, and auth failures."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.extauthz_url = self.base_url + "gateway/remoteauth/auth/api/v1/extauthz"

    def test_extauthz_success(self):
        """Valid credentials on extauthz return 200 and X-Knox-Actor-ID."""
        response = knox_get(
            self.extauthz_url,
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("X-Knox-Actor-ID", response.headers)
        self.assertEqual(response.headers["X-Knox-Actor-ID"], "guest")

    def test_extauthz_additional_path_is_ignored(self):
        """Extra path segments under extauthz still authenticate successfully."""
        response = knox_get(
            self.extauthz_url + "/some/extra/path",
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(response.status_code, 200)
        self.assertIn("X-Knox-Actor-ID", response.headers)
        self.assertEqual(response.headers["X-Knox-Actor-ID"], "guest")

    def test_extauthz_bad_credentials_unauthorized(self):
        """Invalid credentials on extauthz return 401."""
        response = knox_get(
            self.extauthz_url,
            auth=HTTPBasicAuth("baduser", "badpass"),
        )
        self.assertEqual(response.status_code, 401)

    def test_extauthz_missing_credentials(self):
        """Missing Authorization header on extauthz returns 401."""
        response = knox_get(self.extauthz_url)
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
