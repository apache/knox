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

"""End-to-end tests for KNOXTOKEN issuance and JWTProvider federation.

These exercise the ``knoxtoken`` topology (JWTProvider federation) together
with the KNOXTOKEN service exposed by the ``knoxldap`` topology:

1. A JWT is minted from the KNOXTOKEN service using Basic auth (knoxldap).
2. The resulting bearer token is presented to the JWTProvider-protected
   ``knoxtoken`` topology, which must accept it and assert the caller's
   identity.

No other suite issues Knox tokens or authenticates via JWTProvider, so this
file does not overlap with the Basic-auth / preauth coverage elsewhere.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get


class TestKnoxTokenJwt(unittest.TestCase):
    """Mint a Knox JWT and use it against a JWTProvider-federated topology."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # KNOXTOKEN service lives in the knoxldap topology (Basic auth in front).
        self.token_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v1/token"
        # JWTProvider-protected auth service in the knoxtoken topology.
        self.federated_pre_url = self.base_url + "gateway/knoxtoken/auth/api/v1/pre"

    def _issue_token(self, username, password):
        """Return the parsed JSON body of a freshly issued Knox token."""
        response = knox_get(
            self.token_url,
            auth=HTTPBasicAuth(username, password),
        )
        self.assertEqual(
            response.status_code,
            200,
            msg=f"Token issuance failed: {response.status_code} {response.text}",
        )
        return response.json()

    def test_token_endpoint_returns_jwt_and_metadata(self):
        """The KNOXTOKEN service returns a Bearer access_token plus metadata."""
        payload = self._issue_token("guest", "guest-password")

        self.assertIn("access_token", payload)
        self.assertIn("token_type", payload)
        self.assertIn("expires_in", payload)

        self.assertEqual(payload["token_type"], "Bearer")

        # A serialized JWS has three dot-separated segments (header.payload.sig).
        access_token = payload["access_token"]
        self.assertEqual(
            len(access_token.split(".")),
            3,
            msg="access_token does not look like a signed JWT",
        )

    def test_token_requires_authentication(self):
        """The token endpoint must reject anonymous callers with 401."""
        response = knox_get(self.token_url)
        self.assertEqual(response.status_code, 401)

    def test_jwt_grants_access_to_federated_topology(self):
        """A valid Knox JWT authenticates against the JWTProvider topology."""
        access_token = self._issue_token("guest", "guest-password")["access_token"]

        response = knox_get(
            self.federated_pre_url,
            headers={"Authorization": f"Bearer {access_token}"},
        )
        self.assertEqual(
            response.status_code,
            200,
            msg=f"JWT was not accepted: {response.status_code} {response.text}",
        )
        self.assertEqual(response.headers.get("x-knox-actor-username"), "guest")

    def test_federated_topology_requires_token(self):
        """The JWTProvider topology rejects requests that carry no token."""
        response = knox_get(self.federated_pre_url)
        self.assertEqual(response.status_code, 401)

    def test_federated_topology_rejects_invalid_token(self):
        """A malformed bearer token must not be accepted (401)."""
        response = knox_get(
            self.federated_pre_url,
            headers={"Authorization": "Bearer not.a.valid.jwt"},
        )
        self.assertEqual(response.status_code, 401)


if __name__ == "__main__":
    unittest.main()
