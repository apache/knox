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
4. Passcode tokens (a second credential minted alongside the JWT) authenticate
   the same JWTProvider topology and must stay bound to their own token id
   — a verified passcode may not be replayed against another token.

No other suite issues Knox tokens or authenticates via JWTProvider, so this
file does not overlap with the Basic-auth / preauth coverage elsewhere.
"""

import base64
import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_delete, knox_get, knox_put


# pylint: disable=too-many-public-methods
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

    def test_disable_forbidden_for_non_owner_non_whitelisted_user(self):
        """admin may not disable guest's token without being on the renewer whitelist."""
        token_id = self._issue_token(self.guest_auth)["token_id"]

        disable = knox_put(
            self.token_url + "/disable",
            data=token_id,
            auth=self.admin_auth,
        )
        self.assertEqual(disable.status_code, 403)
        body = disable.json()
        self.assertEqual(body.get("setEnabledFlag"), "false")
        self.assertIn("not authorized", body.get("error", "").lower())

    GET_USER_TOKENS_FORBIDDEN_ERROR = (
        "Caller (guest) is not authorized to see other users' tokens."
    )

    def _assert_get_user_tokens_forbidden(self, params):
        """guest must be denied (403) any getUserTokens query it is not scoped to.

        Only users in knox's "can see all tokens" allowlist may list tokens they
        do not own; being on the renewer whitelist does not grant it.
        """
        self._issue_token(self.admin_auth)

        response = knox_get(
            self.token_url + "/getUserTokens",
            params=params,
            auth=self.guest_auth,
        )
        self.assertEqual(
            response.status_code,
            403,
            msg=f"Expected 403 for getUserTokens {params}, got "
            f"{response.status_code}: {response.text}",
        )
        self.assertEqual(
            response.json().get("error"),
            self.GET_USER_TOKENS_FORBIDDEN_ERROR,
        )

    def test_get_user_tokens_forbidden_all_tokens(self):
        """guest may not enumerate every token via allTokens=true."""
        self._assert_get_user_tokens_forbidden({"allTokens": "true"})

    def test_get_user_tokens_forbidden_by_username(self):
        """guest may not list another user's tokens by userName."""
        self._assert_get_user_tokens_forbidden({"userName": "admin"})

    def test_get_user_tokens_forbidden_by_created_by(self):
        """guest may not list another user's tokens by createdBy."""
        self._assert_get_user_tokens_forbidden({"createdBy": "admin"})

    def test_get_user_tokens_forbidden_by_username_or_created_by(self):
        """guest may not list another user's tokens by userNameOrCreatedBy."""
        self._assert_get_user_tokens_forbidden({"userNameOrCreatedBy": "admin"})

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

    def _present_passcode(self, passcode_field):
        """Present a passcode token value to the JWTProvider topology as Basic auth."""
        basic = base64.b64encode(
            ("Passcode:" + passcode_field).encode("utf-8")
        ).decode("ascii")
        return knox_get(
            self.federated_pre_url,
            headers={"Authorization": "Basic " + basic},
        )

    @staticmethod
    def _craft_cross_token_passcode(target_token_id, victim_passcode_field):
        """Pair target_token_id with the victim's raw passcode, re-encoded as a value.

        Decodes ``Base64( Base64(victimId) + "::" + Base64(rawPasscode) )``,
        swaps in the target token id, and re-encodes — the payload an attacker
        who holds one valid passcode would forge for another token id.
        """
        inner = base64.b64decode(victim_passcode_field).decode("utf-8")
        _victim_id_b64, raw_passcode_b64 = inner.split("::")
        target_id_b64 = base64.b64encode(
            target_token_id.encode("utf-8")
        ).decode("ascii")
        crafted_inner = target_id_b64 + "::" + raw_passcode_b64
        return base64.b64encode(crafted_inner.encode("utf-8")).decode("ascii")

    def test_valid_passcode_authenticates(self):
        """Positive control: a token's own passcode authenticates as its owner (200)."""
        payload = self._issue_token(self.guest_auth)
        self.assertIn(
            "passcode",
            payload,
            msg="no passcode field; token state service must be persistent/server-managed",
        )
        response = self._present_passcode(payload["passcode"])
        self.assertEqual(
            response.status_code,
            200,
            msg=f"valid passcode was not accepted: {response.status_code} {response.text}",
        )
        self.assertEqual(response.headers.get("x-knox-actor-username"), "guest")

    def test_passcode_is_bound_to_its_own_token(self):
        """A guest passcode replayed against an admin token id must be rejected (401).

        Warm the verification cache with guest's real passcode (accepted, 200),
        then present that same raw passcode paired with admin's token id. Before
        the fix the cache hit on the passcode alone authenticated the caller as
        admin; the fix keys the cache by token id + passcode, so the forged pair
        falls through to a MAC check against admin's token and fails.
        """
        admin_token_id = self._issue_token(self.admin_auth)["token_id"]
        guest_passcode = self._issue_token(self.guest_auth)["passcode"]

        warm = self._present_passcode(guest_passcode)
        self.assertEqual(
            warm.status_code,
            200,
            msg=f"warm-up passcode was not accepted: {warm.status_code} {warm.text}",
        )
        self.assertEqual(warm.headers.get("x-knox-actor-username"), "guest")

        forged = self._craft_cross_token_passcode(admin_token_id, guest_passcode)
        response = self._present_passcode(forged)
        self.assertEqual(
            response.status_code,
            401,
            msg=f"cross-token passcode must be rejected; got "
            f"{response.status_code}: {response.text}",
        )
        self.assertNotEqual(response.headers.get("x-knox-actor-username"), "admin")


if __name__ == "__main__":
    unittest.main()
