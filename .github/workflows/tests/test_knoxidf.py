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
from urllib.parse import urlparse, parse_qs
from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get, knox_post, get_token_claim, get_token_id_display_text

class TestKnoxIDF(unittest.TestCase):
    def setUp(self):
        # Get the Knox Gateway URL from environment variables
        self.base_url =  gateway_base_url()
        self.knoxidf_ldap_url = f"{self.base_url}gateway/knoxidf-ldap/"
        self.knoxidf_token_url = f"{self.base_url}gateway/knoxidf-token/"
        self.username = "guest"
        self.password = "guest-password"

    def test_discovery(self):
        """
        Test OIDC Discovery endpoint.
        """
        url = f"{self.knoxidf_ldap_url}knoxidf/api/v1/.well-known/openid-configuration"
        print(f"Testing Discovery URL: {url}")
        response = knox_get(url)
        self.assertEqual(response.status_code, 200)
        config = response.json()
        
        # Construct expected values based on dynamic base_url
        expected_issuer = f"{self.knoxidf_ldap_url}knoxidf"
        expected_auth_endpoint = f"{self.knoxidf_ldap_url}knoxidf/api/v1/authorize"
        expected_token_endpoint = f"{self.knoxidf_token_url}knoxidf/api/v1/token"
        expected_userinfo_endpoint = f"{self.knoxidf_token_url}knoxidf/api/v1/userinfo"
        expected_jwks_uri = f"{self.knoxidf_ldap_url}knoxidf/api/v1/jwks"

        self.assertEqual(config.get("issuer"), expected_issuer)
        self.assertEqual(config.get("authorization_endpoint"), expected_auth_endpoint)
        self.assertEqual(config.get("token_endpoint"), expected_token_endpoint)
        self.assertEqual(config.get("userinfo_endpoint"), expected_userinfo_endpoint)
        self.assertEqual(config.get("jwks_uri"), expected_jwks_uri)

        self.assertEqual(config.get("response_types_supported"), ["code"])
        self.assertEqual(config.get("grant_types_supported"), ["authorization_code", "refresh_token"])
        self.assertEqual(config.get("id_token_signing_alg_values_supported"), ["RS256"])
        self.assertEqual(config.get("scopes_supported"), ["openid", "email", "profile", "offline_access"])

    def test_client_credentials_flow(self):
        """
        Test OIDC Client Credentials Flow.
        """
        # 1. Register client
        reg_url = f"{self.knoxidf_ldap_url}knoxidf/api/v1/client/register"
        print(f"Registering client at: {reg_url}")
        data = {
            "redirect_uris": "http://localhost/callback",
            "allowed_scopes": "openid,profile,email,offline_access"
        }
        response = knox_post(
            reg_url,
            data=data,
            auth=HTTPBasicAuth(self.username, self.password),
        )
        self.assertEqual(response.status_code, 200)
        reg_info = response.json()
        print(f"Registration response: {reg_info}")
        client_id = reg_info["client_id"]
        client_secret = reg_info["client_secret"]

        # 2. Get token via client_credentials
        token_url = f"{self.knoxidf_token_url}knoxtoken/api/v1/token"
        print(f"Getting token at: {token_url}")
        data = {
            "grant_type": "client_credentials",
            "scope": "openid",
            "client_id": client_id,
            "client_secret": client_secret
        }
        # ClientCredentialsResource uses Basic Auth for client authentication
        response = knox_post(token_url, data=data, verify=False)
        if response.status_code != 200:
             print(f"Token error response: {response.text}")
        self.assertEqual(response.status_code, 200)
        tokens = response.json()
        self.assertIn("access_token", tokens)
        self.assertEqual(tokens["token_type"], "Bearer")

    def test_authorization_code_flow(self):
        """
        Test OIDC Authorization Code Flow with Refresh Token.
        """
        # 1. Register client
        reg_url = f"{self.knoxidf_ldap_url}knoxidf/api/v1/client/register"
        print(f"Registering client at: {reg_url}")
        data = {
            "redirect_uris": "http://localhost/callback",
            "allowed_scopes": "openid,profile,email,offline_access"
        }
        response = knox_post(
            reg_url,
            data=data,
            auth=HTTPBasicAuth(self.username, self.password),
        )
        self.assertEqual(response.status_code, 200)
        reg_info = response.json()
        print(f"Registration response: {reg_info}")
        client_id = reg_info["client_id"]
        client_secret = reg_info["client_secret"]

        # 2. Authorize (with Basic Auth for the user 'guest')
        auth_url = f"{self.knoxidf_ldap_url}knoxidf/api/v1/authorize"
        params = {
            "response_type": "code",
            "client_id": client_id,
            "redirect_uri": "http://localhost/callback",
            "scope": "openid offline_access",
            "state": "test_state",
            "auto_consent": "true"
        }
        print(f"Authorizing at: {auth_url}")
        # allow_redirects=False to catch the redirect to redirect_uri
        response = knox_get(auth_url, params=params, auth=(self.username, self.password), verify=False, allow_redirects=False)
        
        # Should be a redirect to the callback URL
        self.assertEqual(response.status_code, 303)
        location = response.headers.get("Location")
        self.assertIsNotNone(location)
        self.assertTrue(location.startswith("http://localhost/callback"))
        
        parsed_url = urlparse(location)
        query_params = parse_qs(parsed_url.query)
        self.assertIn("code", query_params)
        self.assertIn("state", query_params)
        self.assertEqual(query_params["state"][0], "test_state")
        code = query_params["code"][0]

        # 3. Exchange code for tokens
        token_url = f"{self.knoxidf_token_url}knoxidf/api/v1/token"
        print(f"Exchanging code for tokens at: {token_url}")
        data = {
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": "http://localhost/callback",
            "client_id": client_id,
            "client_secret": client_secret
        }
        response = knox_post(token_url, data=data, verify=False)
        if response.status_code != 200:
             print(f"Code exchange error: {response.text}")
        self.assertEqual(response.status_code, 200)
        tokens = response.json()
        self.assertIn("access_token", tokens)
        self.assertIn("id_token", tokens)
        self.assertIn("refresh_token", tokens)
        
        refresh_token = tokens["refresh_token"]

        print(f"Refresh token: {refresh_token}")
        refresh_token_id = get_token_claim(refresh_token, 'knox.id')
        print(f"Refresh token knox.id: {refresh_token_id}")

        # 4. Refresh the token (rotation)
        print("Refreshing token...")
        data = {
            "grant_type": "refresh_token",
            "refresh_token": refresh_token,
            "client_id": client_id,
            "client_secret": client_secret
        }
        response = knox_post(token_url, data=data, verify=False)
        self.assertEqual(response.status_code, 200)
        new_tokens = response.json()
        self.assertIn("access_token", new_tokens)
        self.assertIn("refresh_token", new_tokens)
        
        # Verify rotation: new refresh token should be different
        self.assertNotEqual(refresh_token, new_tokens["refresh_token"])

        # 5. Verify old refresh token is invalidated
        print(f"Verifying old refresh token is invalidated...")
        # Use same data (with old refresh_token)
        data_old = {
            "grant_type": "refresh_token",
            "refresh_token": refresh_token,
            "client_id": client_id,
            "client_secret": client_secret
        }
        response = knox_post(token_url, data=data_old, verify=False, headers={"Accept": "application/json"})
        self.assertEqual(response.status_code, 401)
        error_info = response.json()
        display_id = get_token_id_display_text(refresh_token_id)
        self.assertEqual(error_info["status"], "401")
        self.assertIn(f"Unknown token: {display_id}", error_info["message"])

if __name__ == '__main__':
    unittest.main()
