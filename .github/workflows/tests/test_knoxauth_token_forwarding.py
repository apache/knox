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

"""End-to-end tests for forwarding the caller's JWT out of KNOX-AUTH-SERVICE.

``preauth.auth.header.auth.token.name`` makes ``auth/api/v1/pre`` return the
caller's own token in the named response header, which an Envoy ``ext_authz``
or nginx ``auth_request`` filter then copies onto the downstream request.

Four topologies are exercised, plus two that must not change:

* ``knoxauthtoken`` -- the parameter is set. The forwarded header must be the
  minted JWT byte for byte, and must survive the identity assertion re-wrap:
  ``group.principal.mapping`` is configured there, so the request goes through
  the branch of ``AbstractIdentityAssertionFilter`` that discards a Subject and
  builds a fresh one. A unit test can pass while that branch drops the token;
  this is the end-to-end proof that it does not.
* ``knoxauthtokenlimit`` -- the size limit is below any real JWT, so the header
  must be omitted. This endpoint is an authorization gate, so degrading must
  stay a 200 that still carries the identity headers, never a 5xx that fails
  the gate closed for every caller.
* ``knoxauthtokencollide`` -- the token header name is the actor id header
  name. The token must lose that contest, not the identity.
* ``knoxtoken`` and ``knoxldap`` -- the parameter is absent. Neither may grow a
  token header, which is what keeps this feature opt-in.

The JWT is minted from the KNOXTOKEN service in ``knoxldap`` (Basic auth) and
federates into the topologies above because they share the gateway's signing
key -- the same pattern ``test_knoxtoken_jwt.py`` uses.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import (
    basic_auth_get,
    collect_actor_group_values,
    gateway_base_url,
    get_token_claim,
    knox_delete,
    knox_get,
)

TOKEN_HEADER = "x-knox-auth-token"
ACTOR_ID_HEADER = "x-knox-actor-username"
# Default identity assertion maps guest to this group in the new topologies,
# which is what forces the Subject re-wrap the forwarded token has to survive.
MAPPED_GROUP = "forwarded-token-group"


class TestKnoxAuthTokenForwarding(unittest.TestCase):
    """Forward a JWT-federated caller's own token as a response header."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.token_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v1/token"
        self.revoke_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v2/token/revoke"
        self.guest_auth = HTTPBasicAuth("guest", "guest-password")
        self.issued = []

    def tearDown(self):
        while self.issued:
            knox_delete(self.revoke_url, data=self.issued.pop(), auth=self.guest_auth)

    def _mint_jwt(self):
        """Mint a Knox JWT for guest and return its serialized form."""
        minted = knox_get(self.token_url, auth=self.guest_auth)
        self.assertEqual(200, minted.status_code, msg=f"mint failed: {minted.text}")
        access_token = minted.json()["access_token"]
        self.issued.append(access_token)
        return access_token

    def _pre(self, topology, access_token):
        """Call a topology's auth/api/v1/pre with a bearer token."""
        return knox_get(
            self.base_url + f"gateway/{topology}/auth/api/v1/pre",
            headers={"Authorization": f"Bearer {access_token}"},
        )

    def test_forwarded_token_is_the_minted_jwt_byte_for_byte(self):
        """The header carries the bare serialized JWT, with no scheme prefix."""
        access_token = self._mint_jwt()
        response = self._pre("knoxauthtoken", access_token)

        self.assertEqual(200, response.status_code)
        self.assertIn(TOKEN_HEADER, response.headers)
        self.assertEqual(access_token, response.headers[TOKEN_HEADER])
        self.assertNotIn("Bearer", response.headers[TOKEN_HEADER])

    def test_forwarded_token_survives_the_identity_assertion_rewrap(self):
        """Mapped groups prove the re-wrap ran, and the token is still there."""
        access_token = self._mint_jwt()
        response = self._pre("knoxauthtoken", access_token)

        self.assertEqual(200, response.status_code)
        groups = collect_actor_group_values(response)
        self.assertIn(
            MAPPED_GROUP,
            groups,
            msg="group mapping did not run, so this topology is not exercising "
            f"the Subject re-wrap at all; groups were {groups}",
        )
        self.assertEqual(
            access_token,
            response.headers.get(TOKEN_HEADER),
            msg="the token was dropped by the identity assertion re-wrap",
        )

    def test_forwarded_token_names_the_original_caller(self):
        """The token's sub claim is the caller, alongside the actor id header."""
        access_token = self._mint_jwt()
        response = self._pre("knoxauthtoken", access_token)

        self.assertEqual("guest", response.headers.get(ACTOR_ID_HEADER))
        self.assertEqual("guest", get_token_claim(response.headers[TOKEN_HEADER], "sub"))

    def test_a_response_carrying_a_token_is_not_cacheable(self):
        """RFC 6749 section 5.1: a token response must not be stored."""
        response = self._pre("knoxauthtoken", self._mint_jwt())

        self.assertEqual("no-store", response.headers.get("Cache-Control"))

    def test_a_response_without_a_token_keeps_its_cache_semantics(self):
        """The no-store header is only added when a token is actually emitted."""
        response = self._pre("knoxtoken", self._mint_jwt())

        self.assertEqual(200, response.status_code)
        self.assertNotEqual("no-store", response.headers.get("Cache-Control"))

    def test_no_token_is_forwarded_when_the_parameter_is_unset(self):
        """knoxtoken omits the parameter, so its responses must not change."""
        response = self._pre("knoxtoken", self._mint_jwt())

        self.assertEqual(200, response.status_code)
        self.assertNotIn(TOKEN_HEADER, response.headers)
        self.assertEqual("guest", response.headers.get(ACTOR_ID_HEADER))

    def test_no_token_is_forwarded_for_a_basic_auth_caller(self):
        """A Basic-auth identity presented no JWT, so there is nothing to send."""
        response = basic_auth_get(
            self.base_url + "gateway/knoxldap/auth/api/v1/pre", "guest", "guest-password"
        )

        self.assertEqual(200, response.status_code)
        self.assertNotIn(TOKEN_HEADER, response.headers)
        self.assertEqual("guest", response.headers.get(ACTOR_ID_HEADER))

    def test_an_oversized_token_is_omitted_without_failing_the_request(self):
        """Over the size limit the token is dropped; the gate still says 200."""
        response = self._pre("knoxauthtokenlimit", self._mint_jwt())

        self.assertEqual(
            200,
            response.status_code,
            msg="a token too large to forward must not fail the authz gate",
        )
        self.assertNotIn(TOKEN_HEADER, response.headers)
        self.assertEqual("guest", response.headers.get(ACTOR_ID_HEADER))

    def test_a_colliding_header_name_preserves_the_identity_header(self):
        """Configured onto the actor id header, the token is dropped instead."""
        access_token = self._mint_jwt()
        response = self._pre("knoxauthtokencollide", access_token)

        self.assertEqual(200, response.status_code)
        self.assertEqual("guest", response.headers.get(ACTOR_ID_HEADER))
        self.assertNotEqual(access_token, response.headers.get(ACTOR_ID_HEADER))

    def test_an_unauthenticated_request_gets_no_token_header(self):
        """No credential, no identity, and certainly no forwarded token."""
        response = knox_get(self.base_url + "gateway/knoxauthtoken/auth/api/v1/pre")

        self.assertEqual(401, response.status_code)
        self.assertNotIn(TOKEN_HEADER, response.headers)

    def test_a_garbled_bearer_token_is_rejected_rather_than_forwarded(self):
        """An unvalidated token must never reach the response header."""
        response = self._pre("knoxauthtoken", self._mint_jwt() + "tampered")

        self.assertEqual(401, response.status_code)
        self.assertNotIn(TOKEN_HEADER, response.headers)


if __name__ == "__main__":
    unittest.main()
