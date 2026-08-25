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

"""End-to-end tests for KNOXTOKEN issuance, lifecycle, and JWTProvider federation.

These exercise the ``knoxtoken`` topology (JWTProvider federation) together
with the KNOXTOKEN service exposed by the ``knoxldap`` topology:

1. A JWT is minted from the KNOXTOKEN service using Basic auth (knoxldap).
2. The resulting bearer token is presented to the JWTProvider-protected
   ``knoxtoken`` topology, which must accept it and assert the caller's
   identity.
3. Lifecycle operations (renew / revoke / enable / disable) require
   ``knox.token.exp.server-managed=true`` on both the issuing service and
   the JWTProvider so that revocation and disablement are enforced at
   federation time — not just acknowledged by the management API.

No other suite issues Knox tokens or authenticates via JWTProvider, so this
file does not overlap with the Basic-auth / preauth coverage elsewhere.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_delete, knox_get, knox_put


class TestKnoxTokenJwt(unittest.TestCase):
    """Mint a Knox JWT and use it against a JWTProvider-federated topology."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # KNOXTOKEN service lives in the knoxldap topology (Basic auth in front).
        self.token_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v1/token"
        # Non-deprecated lifecycle paths (PUT renew / DELETE revoke).
        self.token_v2_url = self.base_url + "gateway/knoxldap/knoxtoken/api/v2/token"
        # JWTProvider-protected auth service in the knoxtoken topology.
        self.federated_pre_url = self.base_url + "gateway/knoxtoken/auth/api/v1/pre"

        self.guest_auth = HTTPBasicAuth("guest", "guest-password")
        self.admin_auth = HTTPBasicAuth("admin", "admin-password")
        self._minted_tokens = []

    def tearDown(self):
        for access_token in self._minted_tokens:
            self._revoke_quietly(access_token)

    def _revoke_quietly(self, access_token):
        """Revoke a token, ignoring failures (already revoked/expired/etc.)."""
        knox_delete(
            self.token_v2_url + "/revoke",
            data=access_token,
            auth=self.guest_auth,
        )

    def _issue_token(self, auth):
        """Return the parsed JSON body of a freshly issued Knox token."""
        response = knox_get(self.token_url, auth=auth)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"Token issuance failed: {response.status_code} {response.text}",
        )
        payload = response.json()
        self.assertIn("access_token", payload)
        self.assertIn("token_id", payload)
        self._minted_tokens.append(payload["access_token"])
        return payload

    def _federate(self, access_token):
        """Present a bearer token to the JWTProvider-protected topology."""
        return knox_get(
            self.federated_pre_url,
            headers={"Authorization": f"Bearer {access_token}"},
        )

    def _assert_federates(self, access_token, expected_username):
        response = self._federate(access_token)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"JWT was not accepted: {response.status_code} {response.text}",
        )
        self.assertEqual(
            response.headers.get("x-knox-actor-username"),
            expected_username,
        )

    def _assert_federation_rejected(self, access_token):
        """Assert that a bearer token is rejected by the JWTProvider topology."""
        response = self._federate(access_token)
        self.assertEqual(
            response.status_code,
            401,
            msg=f"Expected federation rejection, got {response.status_code}: {response.text}",
        )

    def test_token_endpoint_returns_jwt_and_metadata(self):
        """The KNOXTOKEN service returns a Bearer access_token plus metadata."""
        payload = self._issue_token(self.guest_auth)

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
        access_token = self._issue_token(self.guest_auth)["access_token"]
        self._assert_federates(access_token, "guest")

    def test_federated_topology_requires_token(self):
        """The JWTProvider topology rejects requests that carry no token."""
        response = knox_get(self.federated_pre_url)
        self.assertEqual(response.status_code, 401)

    def test_federated_topology_rejects_malformed_token(self):
        """A structurally malformed bearer token must not be accepted (401)."""
        response = knox_get(
            self.federated_pre_url,
            headers={"Authorization": "Bearer not.a.valid.jwt"},
        )
        self.assertEqual(response.status_code, 401)

    def test_federated_topology_rejects_wrong_signature(self):
        """
        A parseable JWT with a bad signature must fail RS256 verification.
        """
        access_token = self._issue_token(self.guest_auth)["access_token"]

        header, payload, signature = access_token.split(".")
        mid = len(signature) // 2
        replacement = "A" if signature[mid] != "A" else "B"
        tampered_signature = signature[:mid] + replacement + signature[mid + 1 :]
        tampered = ".".join([header, payload, tampered_signature])
        self.assertNotEqual(tampered, access_token)
        self.assertEqual(len(tampered.split(".")), 3)

        self._assert_federation_rejected(tampered)

    def test_revoke_is_enforced_at_federation(self):
        """Mint → revoke → re-present must yield 401 (not just a revoked:true response)."""
        payload = self._issue_token(self.guest_auth)
        access_token = payload["access_token"]
        self._assert_federates(access_token, "guest")

        revoke = knox_delete(
            self.token_v2_url + "/revoke",
            data=access_token,
            auth=self.guest_auth,
        )
        self.assertEqual(
            revoke.status_code,
            200,
            msg=f"Revoke failed: {revoke.status_code} {revoke.text}",
        )
        self.assertEqual(revoke.json().get("revoked"), "true")

        self._assert_federation_rejected(access_token)

    def test_renew_extends_and_token_still_federates(self):
        """A whitelisted renewer gets renewed:true and the token still federates."""
        access_token = self._issue_token(self.guest_auth)["access_token"]

        renew = knox_put(
            self.token_v2_url + "/renew",
            data=access_token,
            auth=self.guest_auth,
        )
        self.assertEqual(
            renew.status_code,
            200,
            msg=f"Renew failed: {renew.status_code} {renew.text}",
        )
        body = renew.json()
        self.assertEqual(body.get("renewed"), "true")
        self.assertIn("expires", body)

        self._assert_federates(access_token, "guest")

    def test_renew_forbidden_for_non_whitelisted_user(self):
        """admin is not on knox.token.renewer.whitelist and must get 403 on renew."""
        access_token = self._issue_token(self.guest_auth)["access_token"]

        renew = knox_put(
            self.token_v2_url + "/renew",
            data=access_token,
            auth=self.admin_auth,
        )
        self.assertEqual(renew.status_code, 403)
        body = renew.json()
        self.assertEqual(body.get("renewed"), "false")
        self.assertIn("not authorized", body.get("error", "").lower())

    def test_revoke_forbidden_for_non_owner_non_whitelisted_user(self):
        """admin may not revoke guest's token without being on the renewer whitelist."""
        access_token = self._issue_token(self.guest_auth)["access_token"]

        revoke = knox_delete(
            self.token_v2_url + "/revoke",
            data=access_token,
            auth=self.admin_auth,
        )
        self.assertEqual(revoke.status_code, 403)
        body = revoke.json()
        self.assertEqual(body.get("revoked"), "false")
        self.assertIn("not authorized", body.get("error", "").lower())

    def test_disable_is_enforced_at_federation(self):
        """Disabling a token must stop federation; re-enabling restores it."""
        payload = self._issue_token(self.guest_auth)
        access_token = payload["access_token"]
        token_id = payload["token_id"]
        self._assert_federates(access_token, "guest")

        disable = knox_put(
            self.token_url + "/disable",
            data=token_id,
            auth=self.guest_auth,
        )
        self.assertEqual(
            disable.status_code,
            200,
            msg=f"Disable failed: {disable.status_code} {disable.text}",
        )
        self.assertEqual(disable.json().get("setEnabledFlag"), "true")
        self.assertEqual(disable.json().get("isEnabled"), "false")
        self._assert_federation_rejected(access_token)

        enable = knox_put(
            self.token_url + "/enable",
            data=token_id,
            auth=self.guest_auth,
        )
        self.assertEqual(
            enable.status_code,
            200,
            msg=f"Enable failed: {enable.status_code} {enable.text}",
        )
        self.assertEqual(enable.json().get("setEnabledFlag"), "true")
        self.assertEqual(enable.json().get("isEnabled"), "true")
        self._assert_federates(access_token, "guest")

    def test_enable_already_enabled_returns_400(self):
        """Enabling an already-enabled token returns 400 ALREADY_ENABLED."""
        token_id = self._issue_token(self.guest_auth)["token_id"]

        enable = knox_put(
            self.token_url + "/enable",
            data=token_id,
            auth=self.guest_auth,
        )
        self.assertEqual(enable.status_code, 400)
        body = enable.json()
        self.assertEqual(body.get("setEnabledFlag"), "false")
        self.assertIn("already enabled", body.get("error", "").lower())

    def test_disable_already_disabled_returns_400(self):
        """Disabling an already-disabled token returns 400 ALREADY_DISABLED."""
        token_id = self._issue_token(self.guest_auth)["token_id"]

        first = knox_put(
            self.token_url + "/disable",
            data=token_id,
            auth=self.guest_auth,
        )
        self.assertEqual(first.status_code, 200)

        second = knox_put(
            self.token_url + "/disable",
            data=token_id,
            auth=self.guest_auth,
        )
        self.assertEqual(second.status_code, 400)
        body = second.json()
        self.assertEqual(body.get("setEnabledFlag"), "false")
        self.assertIn("already disabled", body.get("error", "").lower())


if __name__ == "__main__":
    unittest.main()
