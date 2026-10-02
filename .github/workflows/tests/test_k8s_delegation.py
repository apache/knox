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

"""End-to-end tests for k8s ServiceAccount token delegation via RFC 8693.

These prove that Knox accepts a real Kubernetes ServiceAccount projected token
on the OAuth 2.0 Token Exchange path once the issuing cluster is registered as a
trusted OIDC issuer. The compose stack turns the throwaway k3s cluster into an
OIDC issuer at https://k3s:6443 and exports, to the shared volume mounted at
/k3s, a freshly minted projected token for ServiceAccount test-sa in namespace
test (see docker-compose.yml).

Four topologies are exercised:
  - knoxidf-admin                    -- the KNOXIDF_ADMIN admin APIs: the
                                        TrustedOIDCIssuers registry and the
                                        delegation-policy store, both restricted to
                                        the LDAP 'admin' user (HTTP Basic).
  - knoxidf-token                    -- the JWTProvider token endpoint used for the
                                        same-subject and negative-path exchanges.
  - knoxidf-token-delegation-policy  -- the delegation-enabled token endpoint
                                        (delegation.server.enabled,
                                        delegation.requested.subject.enabled, nested
                                        act claim, passthrough audience) that performs
                                        the policy-enforced delegation exchanges.
  - knoxidf-ldap                     -- mint endpoint used to obtain a genuine Knox
                                        user token for the delegation subject_token.

Because both the issuer registry and the delegation-policy store are gateway-wide
services, an issuer registered -- or a policy seeded -- through the admin topology is
seen by the token-exchange path on the token topologies.

Covered acceptance criteria span two sibling tickets. The harness ticket: real SA
token is a genuine OIDC-issued JWT (AC2), same-subject exchange succeeds (AC3), an
unregistered issuer is rejected without a JWKS fetch (AC6), an expired token is
rejected before signature verification (AC7), and the full admin lifecycle
register/list/refresh/remove works (AC8/AC9). The policy-enforcement ticket
(KNOX-3493): an interactive delegation exchange with an SA actor_token and a
Knox-user subject_token succeeds under a matching policy and is rejected by policy
otherwise (AC4), and a headless exchange with an SA subject_token + requested_subject
succeeds when the policy allows headless delegation and is rejected when it does not
(AC5). Both AC4/AC5 denials are RFC 8693 invalid_request (HTTP 400), the same
policy-denial contract the KNOX-3476 delegation suite (test_delegation.py) asserts.
"""

import base64
import json
import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, get_token_claim, knox_get, knox_post, knox_delete
from delegation_helpers import (
    ISSUED_TOKEN_TYPE_JWT,
    DelegationPolicyAdmin,
    assert_oauth_error,
    aud_values,
    token_exchange,
)

# The LDAP 'admin' user is the only principal the knoxidf-admin ACL permits
# (see conf/topologies/knoxidf-admin.xml and the demo users.ldif).
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# The k3s cluster is configured (service-account-issuer) to issue SA tokens whose
# 'iss' is this URL; it is also the OIDC discovery base Knox registers as trusted.
ISSUER_URL = "https://k3s:6443"

# RFC 8693 token-exchange identifiers.
TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"

# The k8s-bootstrap service writes a projected SA token here on the shared volume.
SA_TOKEN_FILE = "/k3s/sa-token"

# An issuer that is never registered, used to prove unregistered issuers are rejected.
UNREGISTERED_ISSUER = "https://unregistered.example.com"

# A bare-IP issuer URL (TEST-NET-3, RFC 5737). The knoxidf-admin topology sets
# knoxidf.allow.ip.literal.issuer.url=false, so registering this must be rejected.
IP_LITERAL_ISSUER = "https://203.0.113.5:6443"

# The k3s ServiceAccount whose projected token the bootstrap exports (test-sa in namespace
# test); this is the token's 'sub' and, on a delegation exchange, the actor recorded in act.sub.
SA_SUBJECT = "system:serviceaccount:test:test-sa"
# ActorIdentity.fromJwt (gateway-provider-security-jwt) tags a k8s SA subject with this
# authority and an actorId of "<issuer>:<namespace>:<sa-name>" -- the delegation policy's key.
K8S_SA_ACTOR_AUTHORITY = "K8S_SA"
SA_SUBJECT_PREFIX = "system:serviceaccount:"

# Demo LDAP users (uid == cn, password "<user>-password"). tom is the impersonated subject on
# the allow paths; sam is the user a mismatched policy authorizes instead, so the AC4 deny path
# is a genuine policy decision (actor known, subject not permitted) rather than a missing policy.
IMPERSONATED_USER = "tom"
IMPERSONATED_PASSWORD = "tom-password"
OTHER_USER = "sam"

# The delegation topology requires exactly one absolute-URI resource per exchange
# (delegation.enforce.requested.audience.required + .max.one); minted as the token's aud.
DELEGATED_RESOURCE = "https://k8s-delegated-resource"


def _b64url(raw):
    """Base64url-encode raw bytes without padding, as JOSE requires."""
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _decode_segment(token, index):
    """Decode the JOSE header (index 0) or payload (index 1) of a JWT to a dict."""
    segment = token.split(".")[index]
    padding = "=" * (-len(segment) % 4)
    return json.loads(base64.urlsafe_b64decode(segment + padding))


def _unsigned_jwt(payload):
    """Serialize a payload into a header.payload.signature JWT with a dummy signature.

    The negative paths under test (unregistered issuer, expired token) are both
    rejected before Knox verifies the signature, so no real signing key is needed.
    """
    header = {"alg": "RS256", "typ": "JWT", "kid": "test-key"}
    return ".".join([
        _b64url(json.dumps(header, separators=(",", ":")).encode("utf-8")),
        _b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8")),
        _b64url(b"dummy-signature"),
    ])


class TestK8sDelegation(unittest.TestCase):  # pylint: disable=too-many-instance-attributes
    """RFC 8693 token exchange of real k8s ServiceAccount tokens through Knox."""

    def setUp(self):
        base_url = gateway_base_url()
        self.admin_url = base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/trusted-oidc-issuers"
        self.token_url = base_url + "gateway/knoxidf-token/knoxidf/api/v1/token"
        # Delegation-policy admin API and the delegation-enabled exchange endpoint (AC4/AC5),
        # plus the LDAP mint endpoint for the Knox-user subject_token of an interactive exchange.
        self.admin_policy_url = (
            base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/delegation-policies"
        )
        self.delegation_url = (
            base_url + "gateway/knoxidf-token-delegation-policy/knoxidf/api/v1/token"
        )
        self.mint_url = base_url + "gateway/knoxidf-ldap/knoxidf/api/v1/token"
        try:
            with open(SA_TOKEN_FILE, encoding="utf-8") as handle:
                self.sa_token = handle.read().strip()
        except OSError:
            self.skipTest(f"SA token not available at {SA_TOKEN_FILE}")

    def _admin_auth(self):
        """HTTP Basic credentials for the knoxidf-admin API."""
        return HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)

    def _register_issuer(self, issuer_url):
        """Register a trusted OIDC issuer (dynamic JWKS via discovery)."""
        return knox_post(
            self.admin_url,
            json={"issuerUrl": issuer_url, "dynamicJwks": True, "clusterName": "k3s"},
            auth=self._admin_auth(),
        )

    def _list_issuers(self):
        """Return the registered trusted issuers as a list of dicts."""
        response = knox_get(self.admin_url, auth=self._admin_auth())
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def _remove_issuer(self, issuer_url):
        """Deregister a trusted OIDC issuer (idempotent at the service layer)."""
        return knox_delete(
            self.admin_url, params={"issuerUrl": issuer_url}, auth=self._admin_auth()
        )

    def _refresh_jwks(self, issuer_url):
        """Force a re-resolution of the issuer's JWKS URI from its discovery document."""
        return knox_post(
            self.admin_url + "/refresh-jwks",
            params={"issuerUrl": issuer_url},
            auth=self._admin_auth(),
        )

    def _exchange(self, subject_token):
        """Perform a same-subject RFC 8693 token exchange (no actor_token)."""
        return knox_post(
            self.token_url,
            data={
                "grant_type": TOKEN_EXCHANGE_GRANT,
                "subject_token": subject_token,
                "subject_token_type": SUBJECT_TOKEN_TYPE,
            },
        )

    def _issuer_urls(self):
        """Return just the issuerUrl values currently in the registry."""
        return [entry.get("issuerUrl") for entry in self._list_issuers()]

    def test_sa_token_is_a_genuine_oidc_jwt(self):
        """AC2: the exported token is a real k3s-issued JWT for the test ServiceAccount."""
        header = _decode_segment(self.sa_token, 0)
        payload = _decode_segment(self.sa_token, 1)
        # A real signed JWT carries a signing algorithm and key id in its JOSE header.
        self.assertIn("alg", header)
        self.assertIn("kid", header)
        # Knox's verifier tolerates a missing 'typ'; k8s may or may not set it.
        self.assertIn(header.get("typ"), (None, "JWT"))
        self.assertEqual(payload.get("iss"), ISSUER_URL)
        self.assertIn("system:serviceaccount:test:test-sa", payload.get("sub", ""))

    def test_unregistered_issuer_is_rejected(self):
        """AC6: a token from an unregistered issuer is rejected without a JWKS fetch."""
        # Ensure the issuer really is absent from the registry.
        self._remove_issuer(UNREGISTERED_ISSUER)
        token = _unsigned_jwt({
            "iss": UNREGISTERED_ISSUER,
            "sub": "system:serviceaccount:test:test-sa",
            "exp": 4102444800,  # year 2100, so expiry is not what triggers the rejection
        })
        response = self._exchange(token)
        self.assertEqual(response.status_code, 401, response.text)
        self.assertIn("invalid_request", response.text)

    def test_ip_literal_issuer_registration_rejected(self):
        """knoxidf-admin runs with knoxidf.allow.ip.literal.issuer.url=false, so a bare-IP
        issuerUrl is refused at registration rather than failing later on JWKS discovery."""
        response = self._register_issuer(IP_LITERAL_ISSUER)
        self.assertEqual(response.status_code, 400, response.text)
        self.assertIn("invalid_request", response.text)
        # The rejection must keep it out of the registry.
        self.assertNotIn(IP_LITERAL_ISSUER, self._issuer_urls())

    def test_issuer_lifecycle_and_token_exchange(self):
        """AC3/AC7/AC8/AC9: register, exchange a real token, reject an expired one, remove."""
        # Start from a known-clean state (deregister is idempotent -> 204 either way).
        self.assertEqual(self._remove_issuer(ISSUER_URL).status_code, 204)

        # AC8: registering a new trusted issuer returns 201 Created.
        self.assertEqual(self._register_issuer(ISSUER_URL).status_code, 201)

        # AC9: the registry now lists it, and a second registration conflicts.
        self.assertIn(ISSUER_URL, self._issuer_urls())
        self.assertEqual(self._register_issuer(ISSUER_URL).status_code, 409)

        # Refreshing the JWKS URI from discovery is accepted (204 No Content).
        self.assertEqual(self._refresh_jwks(ISSUER_URL).status_code, 204)

        # AC3: same-subject exchange of the real SA token yields a Knox access token.
        exchanged = self._exchange(self.sa_token)
        self.assertEqual(exchanged.status_code, 200, exchanged.text)
        self.assertIn("access_token", exchanged.json())

        # AC7: an expired token for the registered issuer is rejected on expiry,
        # before signature verification (so the dummy signature is never reached).
        expired = _unsigned_jwt({
            "iss": ISSUER_URL,
            "sub": "system:serviceaccount:test:test-sa",
            "exp": 1000000000,  # year 2001
        })
        expired_response = self._exchange(expired)
        self.assertEqual(expired_response.status_code, 401, expired_response.text)
        self.assertIn("Token has expired", expired_response.text)

        # AC9: removing the issuer returns 204 and drops it from the registry.
        self.assertEqual(self._remove_issuer(ISSUER_URL).status_code, 204)
        self.assertNotIn(ISSUER_URL, self._issuer_urls())

        # Once the issuer is gone, even the previously accepted token is rejected.
        after_removal = self._exchange(self.sa_token)
        self.assertEqual(after_removal.status_code, 401, after_removal.text)
        self.assertIn("invalid_request", after_removal.text)

    # -- AC4/AC5: policy-enforced delegation of k8s SA tokens (KNOX-3493) -----------------

    def _ensure_issuer_registered(self):
        """Register the k3s issuer for one test (clean start), removing it on teardown.

        The delegation exchange validates the SA token (actor or subject) against the
        gateway-wide trusted-issuer registry, so the k3s issuer must be registered first.
        """
        self._remove_issuer(ISSUER_URL)
        self.assertEqual(self._register_issuer(ISSUER_URL).status_code, 201)
        self.addCleanup(self._remove_issuer, ISSUER_URL)

    def _sa_actor_id(self):
        """The delegation-policy actorId ActorIdentity.fromJwt derives for the exported SA token.

        Computed from the token itself -- "<iss>:<namespace>:<sa-name>" -- rather than hardcoded,
        so it stays correct if the bootstrap issuer or ServiceAccount ever changes.
        """
        issuer = get_token_claim(self.sa_token, "iss")
        subject = get_token_claim(self.sa_token, "sub")
        return issuer + ":" + subject[len(SA_SUBJECT_PREFIX):]

    def _sa_policy_admin(self):
        """A DelegationPolicyAdmin scoped to the k8s SA actor, cleaned up after the test."""
        policy_admin = DelegationPolicyAdmin(
            self, self.admin_policy_url, self._admin_auth(),
            (K8S_SA_ACTOR_AUTHORITY, self._sa_actor_id()))
        policy_admin.delete_if_present()
        self.addCleanup(policy_admin.delete_if_present)
        return policy_admin

    def _mint_user_token(self, username, password):
        """Mint a genuine Knox JWT for a demo LDAP user via the knoxidf-ldap endpoint."""
        response = knox_get(self.mint_url, auth=HTTPBasicAuth(username, password))
        self.assertEqual(response.status_code, 200, response.text)
        token = response.json().get("access_token")
        self.assertTrue(token, response.text)
        return token

    def _assert_sa_delegated_token(self, response, impersonated, resource):
        """Assert a 200 delegation exchange whose minted token records the SA acting for a user."""
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        minted = body.get("access_token")
        self.assertTrue(minted, response.text)
        self.assertEqual(body.get("issued_token_type"), ISSUED_TOKEN_TYPE_JWT, response.text)
        # sub is the impersonated user; the nested act claim records the SA as the acting party.
        self.assertEqual(get_token_claim(minted, "sub"), impersonated, response.text)
        act = get_token_claim(minted, "act")
        self.assertIsInstance(act, dict, f"expected a nested act claim, got: {act}")
        self.assertEqual(act.get("sub"), SA_SUBJECT, response.text)
        # The requested resource is minted as the token's audience (passthrough validator).
        self.assertIn(resource, aud_values(minted), response.text)

    def test_ac4_sa_actor_token_delegation_succeeds_with_matching_policy(self):
        """AC4: SA actor_token + Knox-user subject_token succeeds when a policy authorizes it."""
        self._ensure_issuer_registered()
        self._sa_policy_admin().register(
            can_act_for_users=[IMPERSONATED_USER], resource_policy={DELEGATED_RESOURCE: []})

        # subject_token is the impersonated Knox user; actor_token is the real k3s SA token.
        subject_token = self._mint_user_token(IMPERSONATED_USER, IMPERSONATED_PASSWORD)
        response = token_exchange(
            self.delegation_url, subject_token,
            resources=[DELEGATED_RESOURCE], actor_token=self.sa_token)
        self._assert_sa_delegated_token(response, IMPERSONATED_USER, DELEGATED_RESOURCE)

    def test_ac4_sa_actor_token_delegation_rejected_without_matching_policy(self):
        """AC4: the same exchange is rejected when no policy lets the SA act for the user."""
        self._ensure_issuer_registered()
        # A policy exists for the SA actor but authorizes a different user, so acting for the
        # impersonated user is an active policy denial rather than a merely missing policy.
        self._sa_policy_admin().register(
            can_act_for_users=[OTHER_USER], resource_policy={DELEGATED_RESOURCE: []})

        subject_token = self._mint_user_token(IMPERSONATED_USER, IMPERSONATED_PASSWORD)
        response = token_exchange(
            self.delegation_url, subject_token,
            resources=[DELEGATED_RESOURCE], actor_token=self.sa_token)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")

    def test_ac5_sa_headless_delegation_succeeds_when_allowed(self):
        """AC5: headless exchange (SA subject_token + requested_subject) succeeds when allowed."""
        self._ensure_issuer_registered()
        self._sa_policy_admin().register(
            allow_headless_exchange=True, can_act_for_users=[IMPERSONATED_USER],
            resource_policy={DELEGATED_RESOURCE: []})

        # The SA token is the subject_token and, headless, the acting party; tom is impersonated.
        response = token_exchange(
            self.delegation_url, self.sa_token,
            resources=[DELEGATED_RESOURCE], requested_subject=IMPERSONATED_USER)
        self._assert_sa_delegated_token(response, IMPERSONATED_USER, DELEGATED_RESOURCE)

    def test_ac5_sa_headless_delegation_rejected_when_not_allowed(self):
        """AC5: the same headless request is denied when the policy forbids headless exchange."""
        self._ensure_issuer_registered()
        # Identical grant to the success case except headless exchange is not permitted, so the
        # headless flag is provably the sole cause of the denial.
        self._sa_policy_admin().register(
            allow_headless_exchange=False, can_act_for_users=[IMPERSONATED_USER],
            resource_policy={DELEGATED_RESOURCE: []})

        response = token_exchange(
            self.delegation_url, self.sa_token,
            resources=[DELEGATED_RESOURCE], requested_subject=IMPERSONATED_USER)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")


if __name__ == "__main__":
    unittest.main()
