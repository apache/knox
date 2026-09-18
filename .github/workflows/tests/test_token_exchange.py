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

"""End-to-end tests for RFC 8693 token exchange through a running Knox gateway.

These run in the default docker-compose build (no separate compose stack): the
subject token is a genuine Knox JWT minted by the KNOXIDF token endpoint on the
``knoxidf-ldap`` topology (a request with no grant_type falls through to the base
KNOXTOKEN minting path, issuing a standard token for the Basic-authenticated
user), then presented to the JWTProvider token-exchange endpoint. That topology
sets ``knoxidf.knox.token.limit.per.user=-1``, so minting repeatedly across tests
is not capped -- unlike the server-managed KNOXTOKEN service on ``knoxldap``,
whose gateway-wide per-user token limit is shared with every other test.
Because a Knox-issued token verifies against the gateway's own public key, no
trusted-issuer registration is needed (unlike the external-issuer k8s flow in
test_k8s_delegation.py).

The topologies exercised, all fronting the KNOXIDF token endpoint:
  - knoxidf-ldap                    -- mint endpoint; issues subject tokens with no
                                       aud claim (per-user limit -1, uncapped).
  - knoxidf-ldap-aud                -- mint endpoint that stamps a fixed aud claim
                                       (https://recipient1, https://recipient2) via
                                       the default 'static' audience validator.
  - knoxidf-token                   -- delegation disabled (the default); used for the
                                       same-subject happy path and to prove a delegation
                                       exchange is rejected outright.
  - knoxidf-token-delegation        -- delegation.server.enabled=true plus both
                                       requested-audience enforcement flags; used for the
                                       missing/multiple-audience rejections and to prove a
                                       same-subject exchange still succeeds there.
  - knoxidf-token-same-subject-aud  -- token.exchange.same.subject.requested.audience.enabled
                                       =true with a 'passthrough' audience validator
                                       (KNOX-3461); authorizes a requested audience on a
                                       same-subject exchange against the subject token's
                                       own aud claim.
  - knoxidf-token-passthrough       -- fail-safe control: same passthrough validator but
                                       the flag left off (default), so a requested audience
                                       is dropped rather than minted.

Covered acceptance criteria (parent KNOX-3455, split across sub-tasks that all land on
master):

KNOX-3465 -- backed by product code already on master:
  - same-subject exchange returns the expected sub / iss / issued_token_type;
  - a same-subject exchange whose subject token carries no act claim succeeds and the
    minted token likewise carries no act claim (the act-present permutation is covered
    by unit tests -- it needs a seeded delegation policy that has no REST admin API);
  - a delegation exchange with a missing audience is rejected (invalid_request);
  - a delegation exchange with more than one audience is rejected (invalid_request);
  - a delegation exchange against a delegation-disabled topology is rejected with
    "Delegation is not enabled for this topology";
  - a same-subject exchange against a delegation-enabled topology still succeeds.

KNOX-3466 -- same-subject requested-audience authorization, backed by the KNOX-3461
gateway code. These fail until KNOX-3461 is in the image:
  - flag on + requested audience carried by the subject token's aud -> succeeds and the
    exchanged token carries that audience;
  - flag on + requested audience NOT in the subject token's aud -> invalid_target;
  - flag on + subject token has no aud at all -> invalid_target;
  - flag off (the fail-safe default) + passthrough validator -> the requested audience is
    dropped, not minted, even though the validator would otherwise pass it through;
  - the flag is orthogonal to delegation: an actor_token exchange against the flag-on
    topology (which does not enable delegation) is still rejected outright.

KNOX-3467 -- requested-scope cases; deferred until scope support lands.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import (
    gateway_base_url,
    get_token_claim,
    knox_delete,
    knox_get,
    knox_post,
    knox_put,
)

# RFC 8693 token-exchange identifiers.
TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
JWT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"
# KNOXIDF always mints a JWT, so the exchange response advertises the JWT URN.
ISSUED_TOKEN_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt"

# The demo LDAP 'guest' user; the knoxldap KNOXTOKEN service authenticates it via Basic.
GUEST_USER = "guest"
GUEST_PASSWORD = "guest-password"

# Fixed audiences stamped onto tokens minted by the knoxidf-ldap-aud topology
# (knoxidf.knox.token.audiences). The same-subject requested-audience tests request one of
# these (authorized against the subject token's aud) or UNAUTHORIZED_AUDIENCE (which the
# subject token does not carry, so it must be rejected).
SUBJECT_AUDIENCE = "https://recipient1"
OTHER_SUBJECT_AUDIENCE = "https://recipient2"
UNAUTHORIZED_AUDIENCE = "https://recipient3"

# The demo LDAP 'admin' principal -- the only one KNOXIDF_ADMIN.acl (admin;*;*) on the
# knoxidf-admin topology permits, and how the delegation-policy admin REST API is reached.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# ActorIdentity.fromJwt (gateway-provider-security-jwt) tags every non-k8s-serviceaccount JWT
# subject with the fixed authority "USER" and actorId = the JWT's own sub. Both the subject and
# actor tokens below are guest-minted, so the actor registers as (USER, guest) -- matching the
# subject name (guest) the policy must be allowed to act for.
DELEGATION_POLICY_ACTOR_AUTHORITY = "USER"
DELEGATION_POLICY_ACTOR_ID = GUEST_USER
DELEGATION_POLICY_RESOURCE = "https://delegated-resource"


class TestTokenExchange(unittest.TestCase):  # pylint: disable=too-many-instance-attributes
    """RFC 8693 same-subject and delegation-gating behavior through Knox.

    The fixture fronts every KNOXIDF endpoint the suite touches -- six mint/exchange
    topologies plus the delegation-policy admin API -- so it legitimately carries more
    than the default instance-attribute budget.
    """

    def setUp(self):
        base_url = gateway_base_url()
        # KNOXIDF token endpoint (Basic auth) on knoxidf-ldap, used only to mint a real Knox
        # JWT to exchange: a request with no grant_type falls through to the base KNOXTOKEN
        # minting path and issues a standard token for guest. This topology sets a per-user
        # token limit of -1, so repeated minting across tests is not capped (the server-managed
        # KNOXTOKEN service on knoxldap enforces a gateway-wide per-user limit shared by all tests).
        self.mint_url = base_url + "gateway/knoxidf-ldap/knoxidf/api/v1/token"
        # Same mint path but on a topology that stamps a fixed aud claim (SUBJECT_AUDIENCE,
        # OTHER_SUBJECT_AUDIENCE) onto every token, for the same-subject requested-audience tests.
        self.aud_mint_url = base_url + "gateway/knoxidf-ldap-aud/knoxidf/api/v1/token"
        # Token-exchange endpoints: delegation disabled vs. delegation enabled.
        self.exchange_url = base_url + "gateway/knoxidf-token/knoxidf/api/v1/token"
        self.delegation_exchange_url = (
            base_url + "gateway/knoxidf-token-delegation/knoxidf/api/v1/token"
        )
        # KNOX-3461 same-subject requested-audience exchange endpoints (both passthrough
        # validator): the flag is on for the first, off (fail-safe default) for the second.
        self.same_subject_aud_exchange_url = (
            base_url + "gateway/knoxidf-token-same-subject-aud/knoxidf/api/v1/token"
        )
        self.passthrough_exchange_url = (
            base_url + "gateway/knoxidf-token-passthrough/knoxidf/api/v1/token"
        )
        # Delegation-policy admin REST API (KNOXIDF_ADMIN role on the knoxidf-admin topology).
        # The policy store it writes to is a gateway-wide singleton, so a policy registered here
        # is seen by the exchange performed on knoxidf-token-delegation.
        self.admin_policy_url = (
            base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/delegation-policies"
        )
        self.guest_auth = HTTPBasicAuth(GUEST_USER, GUEST_PASSWORD)
        self.admin_auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)

    def _mint_subject_token(self):
        """Mint and return a genuine Knox JWT for the guest user."""
        response = knox_get(self.mint_url, auth=self.guest_auth)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"subject-token minting failed: {response.status_code} {response.text}",
        )
        access_token = response.json().get("access_token")
        self.assertTrue(access_token, "KNOXTOKEN did not return an access_token")
        return access_token

    def _mint_subject_token_with_aud(self):
        """Mint a guest JWT that carries the fixed aud claim from knoxidf-ldap-aud."""
        response = knox_get(self.aud_mint_url, auth=self.guest_auth)
        self.assertEqual(
            response.status_code,
            200,
            msg=f"aud subject-token minting failed: {response.status_code} {response.text}",
        )
        access_token = response.json().get("access_token")
        self.assertTrue(access_token, "KNOXTOKEN did not return an access_token")
        return access_token

    @staticmethod
    def _aud_values(token):
        """Return a token's aud claim as a list (JWT aud may serialize as a string or a list)."""
        aud = get_token_claim(token, "aud")
        if aud is None:
            return []
        return aud if isinstance(aud, list) else [aud]

    def _exchange(self, url, subject_token, resources=None, actor_token=None):
        """POST an RFC 8693 token exchange, returning the raw response.

        resources is conveyed as (possibly repeated) ``resource`` form values;
        an actor_token (with its required type) turns the request into a
        delegation exchange.
        """
        data = [
            ("grant_type", TOKEN_EXCHANGE_GRANT),
            ("subject_token", subject_token),
            ("subject_token_type", JWT_TOKEN_TYPE),
        ]
        if actor_token is not None:
            data.append(("actor_token", actor_token))
            data.append(("actor_token_type", JWT_TOKEN_TYPE))
        for resource in resources or []:
            data.append(("resource", resource))
        return knox_post(url, data=data)

    def _assert_oauth_error(self, response, expected_status, expected_error, message_substring):
        """Assert an RFC 6749 §5.2 style JSON OAuth error with the expected fields."""
        self.assertEqual(
            response.status_code,
            expected_status,
            msg=f"unexpected status: {response.status_code} {response.text}",
        )
        try:
            body = response.json()
        except ValueError:
            body = {}
        self.assertEqual(body.get("error"), expected_error, response.text)
        description = body.get("error_description", "")
        self.assertIn(message_substring, description, response.text)

    # ---- KNOX-3475: delegation-policy status (active/revoked) admin helpers ----
    # Every helper operates on the one (USER, guest) policy this test needs, read straight from
    # the module constants, so none of them thread the actor/resource identity through as args.

    def _find_policy_registration_id(self):
        """Return the registrationId of the (USER, guest) policy, or None."""
        response = knox_get(
            self.admin_policy_url,
            params={"actorAuthority": DELEGATION_POLICY_ACTOR_AUTHORITY},
            auth=self.admin_auth,
        )
        self.assertEqual(response.status_code, 200, response.text)
        for policy in response.json().get("policies", []):
            if policy.get("actorId") == DELEGATION_POLICY_ACTOR_ID:
                return policy.get("registrationId")
        return None

    def _delete_policy_if_present(self):
        """Best-effort cleanup: remove any existing (USER, guest) policy (idempotent)."""
        registration_id = self._find_policy_registration_id()
        if registration_id is not None:
            knox_delete(self.admin_policy_url + "/" + registration_id, auth=self.admin_auth)

    @staticmethod
    def _policy_body(status):
        """The (USER, guest) delegation-policy request body carrying the given status."""
        return {
            "actorAuthority": DELEGATION_POLICY_ACTOR_AUTHORITY,
            "actorId": DELEGATION_POLICY_ACTOR_ID,
            "status": status,
            "canActForUsers": [GUEST_USER],
            "resourcePolicy": {DELEGATION_POLICY_RESOURCE: []},
        }

    def _register_policy(self, status):
        """POST a fresh delegation policy with the given status. Returns its registrationId."""
        response = knox_post(
            self.admin_policy_url, json=self._policy_body(status), auth=self.admin_auth,
        )
        self.assertEqual(response.status_code, 201, response.text)
        return response.json()["registrationId"]

    def _set_policy_status(self, registration_id, status):
        """PUT a full-replace update of the (USER, guest) policy to the given status.

        Identity fields (actorAuthority/actorId/createdBy/createdAt) are immutable server-side,
        so this updates the same row rather than creating a new one -- the "revoked, not deleted"
        scenario the bug is about.
        """
        response = knox_put(
            self.admin_policy_url + "/" + registration_id,
            json=self._policy_body(status),
            auth=self.admin_auth,
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def test_delegation_exchange_denied_after_policy_revoked(self):
        """KNOX-3475: an ACTIVE policy authorizes a delegation exchange; revoking the SAME policy
        (status="revoked", not deleted) denies the identical exchange.

        This is the first end-to-end happy-path delegation exchange in the suite -- it needs a
        seeded active policy, which the KNOXIDF_ADMIN REST API now provides.
        """
        # Known-clean start: (USER, guest) is a process-wide row in the gateway-wide H2 policy
        # store and could carry over from a previous run. Clean up afterwards regardless of outcome.
        self._delete_policy_if_present()
        self.addCleanup(self._delete_policy_if_present)

        registration_id = self._register_policy("active")

        subject_token = self._mint_subject_token()
        actor_token = self._mint_subject_token()

        # ACTIVE: the delegation exchange is authorized end to end.
        allowed = self._exchange(
            self.delegation_exchange_url, subject_token,
            resources=[DELEGATION_POLICY_RESOURCE], actor_token=actor_token,
        )
        self.assertEqual(allowed.status_code, 200, allowed.text)
        self.assertTrue(
            allowed.json().get("access_token"), "authorized exchange did not return an access_token"
        )

        # Revoke the SAME policy record (full-replace via PUT /{registrationId}) -- not deleted.
        revoked = self._set_policy_status(registration_id, "revoked")
        self.assertEqual(revoked.get("status"), "revoked", revoked)

        # REVOKED: the identical exchange (same tokens, same resource) is now denied by policy.
        # Reusing the same subject/actor tokens isolates the policy's status as the only variable
        # that changed between the two exchanges.
        denied = self._exchange(
            self.delegation_exchange_url, subject_token,
            resources=[DELEGATION_POLICY_RESOURCE], actor_token=actor_token,
        )
        self._assert_oauth_error(denied, 400, "invalid_request", "rejected by policy")

    def test_same_subject_exchange_returns_expected_claims(self):
        """A same-subject exchange succeeds and preserves the subject's identity."""
        subject_token = self._mint_subject_token()
        response = self._exchange(self.exchange_url, subject_token)
        self.assertEqual(response.status_code, 200, response.text)

        body = response.json()
        exchanged = body.get("access_token")
        self.assertTrue(exchanged, "exchange did not return an access_token")
        # A serialized JWS has three dot-separated segments (header.payload.signature).
        self.assertEqual(len(exchanged.split(".")), 3, "access_token is not a JWT")
        # RFC 8693: KNOXIDF always mints a JWT, so it advertises the JWT URN.
        self.assertEqual(body.get("issued_token_type"), ISSUED_TOKEN_TYPE_JWT, response.text)

        # sub is preserved from the subject token; iss is present on the minted token.
        self.assertEqual(get_token_claim(exchanged, "sub"), GUEST_USER)
        self.assertTrue(get_token_claim(exchanged, "iss"), "minted token has no iss claim")

    def test_same_subject_exchange_without_act_claim_succeeds(self):
        """no-act permutation: a subject token with no act claim exchanges cleanly.

        The minted token likewise carries no act claim -- a same-subject exchange does
        not introduce an actor chain. (The act-present permutation is exercised by the
        TokenExchangeHandler unit tests; producing an act-carrying token end-to-end
        needs a seeded delegation policy, for which there is no REST admin API.)
        """
        subject_token = self._mint_subject_token()
        self.assertIsNone(get_token_claim(subject_token, "act"),
                          "precondition: freshly minted subject token must have no act claim")

        response = self._exchange(self.exchange_url, subject_token)
        self.assertEqual(response.status_code, 200, response.text)
        exchanged = response.json().get("access_token")
        self.assertTrue(exchanged, "exchange did not return an access_token")
        self.assertIsNone(get_token_claim(exchanged, "act"),
                          "same-subject exchange must not add an act claim")

    def test_delegation_exchange_rejected_when_delegation_disabled(self):
        """An actor_token exchange against a delegation-disabled topology is rejected."""
        subject_token = self._mint_subject_token()
        actor_token = self._mint_subject_token()
        response = self._exchange(
            self.exchange_url, subject_token, resources=["https://recipient"],
            actor_token=actor_token,
        )
        self._assert_oauth_error(response, 400, "invalid_request",
                                 "Delegation is not enabled for this topology")

    def test_delegation_exchange_missing_audience_rejected(self):
        """On a delegation topology enforcing audience-required, a missing audience is rejected."""
        subject_token = self._mint_subject_token()
        actor_token = self._mint_subject_token()
        response = self._exchange(
            self.delegation_exchange_url, subject_token, actor_token=actor_token,
        )
        self._assert_oauth_error(response, 400, "invalid_request",
                                 "audience or resource value is required")

    def test_delegation_exchange_multiple_audiences_rejected(self):
        """On a delegation topology enforcing max-one, more than one audience is rejected."""
        subject_token = self._mint_subject_token()
        actor_token = self._mint_subject_token()
        response = self._exchange(
            self.delegation_exchange_url, subject_token,
            resources=["https://recipient1", "https://recipient2"], actor_token=actor_token,
        )
        self._assert_oauth_error(response, 400, "invalid_request",
                                 "Exactly one combined audience or resource value is allowed")

    def test_same_subject_exchange_succeeds_on_delegation_enabled_topology(self):
        """A same-subject exchange is unaffected by the delegation flags and still succeeds."""
        subject_token = self._mint_subject_token()
        response = self._exchange(self.delegation_exchange_url, subject_token)
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        self.assertTrue(body.get("access_token"), "exchange did not return an access_token")
        self.assertEqual(body.get("issued_token_type"), ISSUED_TOKEN_TYPE_JWT, response.text)
        self.assertEqual(get_token_claim(body["access_token"], "sub"), GUEST_USER)

    # ---- KNOX-3466: same-subject requested-audience authorization (KNOX-3461 code) ----
    # These exercise token.exchange.same.subject.requested.audience.enabled and therefore fail
    # until the KNOX-3461 gateway code is present in the image.

    def test_same_subject_requested_audience_authorized_succeeds(self):
        """Flag on: a requested audience the subject token already carries is honored and minted."""
        subject_token = self._mint_subject_token_with_aud()
        # Precondition: the subject token carries the audience we will request AND a second,
        # unrequested one -- so asserting the exchanged token carries *exactly* the requested
        # audience below rules out an implementation that ignores the request and simply copies
        # the subject token's whole aud set through.
        subject_auds = self._aud_values(subject_token)
        self.assertIn(SUBJECT_AUDIENCE, subject_auds,
                      "precondition: subject token must carry the requested audience in its aud")
        self.assertIn(OTHER_SUBJECT_AUDIENCE, subject_auds,
                      "precondition: subject token must also carry a second, unrequested audience")

        response = self._exchange(
            self.same_subject_aud_exchange_url, subject_token, resources=[SUBJECT_AUDIENCE],
        )
        self.assertEqual(response.status_code, 200, response.text)
        exchanged = response.json().get("access_token")
        self.assertTrue(exchanged, "exchange did not return an access_token")
        self.assertEqual(get_token_claim(exchanged, "sub"), GUEST_USER)
        # Only the requested audience is minted onto the exchanged token -- not the subject
        # token's full aud set. Equality (not membership) is what distinguishes "honored the
        # request" from "passed every subject audience through, ignoring the request".
        self.assertEqual(set(self._aud_values(exchanged)), {SUBJECT_AUDIENCE},
                         "exchanged token must carry exactly the requested audience")

    def test_same_subject_requested_audience_unauthorized_rejected(self):
        """Flag on: a requested audience the subject token does not carry is rejected."""
        subject_token = self._mint_subject_token_with_aud()
        # The subject token must carry a non-empty aud, just not the one we request. Asserting it
        # is non-empty keeps this distinct from the has-no-aud-at-all boundary case below: here the
        # rejection is specifically "this audience isn't among the subject's", not "no aud exists".
        subject_auds = self._aud_values(subject_token)
        self.assertTrue(subject_auds,
                        "precondition: subject token must carry at least one audience")
        self.assertNotIn(UNAUTHORIZED_AUDIENCE, subject_auds,
                         "precondition: subject token must NOT carry the unauthorized audience")

        response = self._exchange(
            self.same_subject_aud_exchange_url, subject_token, resources=[UNAUTHORIZED_AUDIENCE],
        )
        self._assert_oauth_error(response, 400, "invalid_target",
                                 "The requested audience is not authorized for this subject")

    def test_same_subject_requested_audience_rejected_when_subject_has_no_aud(self):
        """Flag on: requesting any audience when the subject token has no aud at all is rejected."""
        subject_token = self._mint_subject_token()
        self.assertEqual(self._aud_values(subject_token), [],
                         "precondition: subject token must carry no aud claim")

        response = self._exchange(
            self.same_subject_aud_exchange_url, subject_token, resources=[SUBJECT_AUDIENCE],
        )
        self._assert_oauth_error(response, 400, "invalid_target",
                                 "The requested audience is not authorized for this subject")

    def test_same_subject_requested_audience_dropped_when_disabled(self):
        """Fail-safe default: with the flag off, a passthrough validator still drops the audience.

        The exchange succeeds but the requested audience is NOT minted onto the exchanged token --
        a passthrough validator alone cannot mint an arbitrarily-audienced token.
        """
        subject_token = self._mint_subject_token_with_aud()
        response = self._exchange(
            self.passthrough_exchange_url, subject_token, resources=[SUBJECT_AUDIENCE],
        )
        self.assertEqual(response.status_code, 200, response.text)
        exchanged = response.json().get("access_token")
        self.assertTrue(exchanged, "exchange did not return an access_token")
        self.assertNotIn(SUBJECT_AUDIENCE, self._aud_values(exchanged),
                         "with the flag off the requested audience must be dropped, not minted")

    def test_delegation_exchange_rejected_on_same_subject_audience_topology(self):
        """The same-subject flag is orthogonal to delegation: actor_token exchange still rejected.

        knoxidf-token-same-subject-aud enables the requested-audience flag but NOT
        delegation.server.enabled, so a delegation (actor_token) exchange must still be refused.
        """
        subject_token = self._mint_subject_token_with_aud()
        actor_token = self._mint_subject_token_with_aud()
        response = self._exchange(
            self.same_subject_aud_exchange_url, subject_token, resources=[SUBJECT_AUDIENCE],
            actor_token=actor_token,
        )
        self._assert_oauth_error(response, 400, "invalid_request",
                                 "Delegation is not enabled for this topology")


if __name__ == "__main__":
    unittest.main()
