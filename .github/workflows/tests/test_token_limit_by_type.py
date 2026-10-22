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

"""End-to-end tests for counting the per-user token limit per token type.

By default, the API issuing a token counts only its own token types towards the user's
knox.token.limit.per.user: CLIENTID counts client credentials, APIKEY counts API keys and
KNOXTOKEN counts JWTs. With knox.token.limit.per.user.by.type.enabled=false every token of
the user (except KnoxSSO cookies) counts, no matter which API issued it.

Topologies (bind-mounted into the CI gateway):

* tokenlimitbytype -- CLIENTID, APIKEY and KNOXTOKEN, each with a limit of 2 (default counting).
* tokenlimit-combined -- KNOXTOKEN with a limit of 2 counted across all types (by.type disabled).

The token state is shared by all topologies and test files, so each test mints tokens on behalf
of (doAs) a fresh, random user to start from zero tokens.
"""

import unittest
import uuid

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, get_token_claim, knox_get, knox_post

LIMIT = 2
LIMIT_EXCEEDED = "token limit exceeded"


class TestTokenLimitByType(unittest.TestCase):
    """Client credentials, API keys and JWTs have separate per-user limits by default."""

    def setUp(self):
        self.base_url = gateway_base_url()
        by_type = self.base_url + "gateway/tokenlimitbytype/"
        self.client_credentials_url = by_type + "clientid/api/v1/oauth/credentials"
        self.api_key_url = by_type + "apikey/api/v1/auth/key"
        self.token_url = by_type + "knoxtoken/api/v1/token"
        self.combined_token_url = (
            self.base_url + "gateway/tokenlimit-combined/knoxtoken/api/v1/token"
        )
        self.admin_auth = HTTPBasicAuth("admin", "admin-password")
        self.user = "tokenlimit-" + uuid.uuid4().hex[:12]

    def _client_credentials(self):
        return knox_post(
            self.client_credentials_url,
            auth=self.admin_auth,
            params={"doAs": self.user},
        )

    def _api_key(self):
        return knox_post(
            self.api_key_url, auth=self.admin_auth, params={"doAs": self.user}
        )

    def _jwt(self, url=None):
        return knox_get(
            url or self.token_url, auth=self.admin_auth, params={"doAs": self.user}
        )

    def _assert_issued(self, response, what):
        self.assertEqual(
            response.status_code,
            200,
            msg=f"{what} for {self.user} was not issued: "
            f"{response.status_code} {response.text}",
        )

    def _assert_limit_exceeded(self, response, what):
        self.assertEqual(
            response.status_code,
            403,
            msg=f"{what} for {self.user} should have hit the token limit: "
            f"{response.status_code} {response.text}",
        )
        self.assertIn(LIMIT_EXCEEDED, response.text)

    def _mint_up_to_limit(self, mint, what):
        for _ in range(LIMIT):
            self._assert_issued(mint(), what)

    def test_jwt_is_issued_for_the_impersonated_user(self):
        """Tokens are minted for the random doAs user, isolating the counts."""
        response = self._jwt()
        self._assert_issued(response, "JWT")
        access_token = response.json()["access_token"]
        self.assertEqual(get_token_claim(access_token, "sub"), self.user)

    def test_jwts_ignore_client_credentials_and_api_keys(self):
        """Client credentials and API keys do not count towards the JWT limit."""
        self._mint_up_to_limit(self._client_credentials, "client credential")
        self._mint_up_to_limit(self._api_key, "API key")

        self._mint_up_to_limit(self._jwt, "JWT")
        self._assert_limit_exceeded(self._jwt(), "JWT")

    def test_client_credentials_and_api_keys_ignore_jwts(self):
        """JWTs do not count towards the client credential / API key limits."""
        self._mint_up_to_limit(self._jwt, "JWT")

        self._mint_up_to_limit(self._client_credentials, "client credential")
        self._assert_limit_exceeded(self._client_credentials(), "client credential")

        self._mint_up_to_limit(self._api_key, "API key")
        self._assert_limit_exceeded(self._api_key(), "API key")

    def test_combined_counting_includes_client_credentials(self):
        """With by.type disabled, the KNOXTOKEN limit counts the user's client credentials."""
        self._mint_up_to_limit(self._client_credentials, "client credential")

        self._assert_limit_exceeded(
            self._jwt(self.combined_token_url), "JWT (combined counting)"
        )
        self._assert_issued(self._jwt(), "JWT (counted by type)")


if __name__ == "__main__":
    unittest.main()
