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

"""End-to-end tests for multi-hop RFC 8693 delegation through a running Knox gateway (KNOX-3520).

test_delegation.py covers a single delegated hop. This suite re-exchanges the minted token:
the service a token was addressed to presents it as the subject_token of the next exchange,
with its own actor_token, so the actor chain grows one nested act claim per hop. A
re-exchanged token does not carry the token-service audience (knox.token.audiences); it is
accepted only because its issuer is listed in
token.exchange.subject.token.policy.audiences.issuers and its aud intersects the presenting
actor's subjectAudiences policy field. The negative tests prove that this is the only
relaxation.

Topologies exercised:
  - knoxidf-ldap-aud              -- mint endpoint for first-hop subject and actor tokens; every
                                     token carries aud https://recipient1 (the token-service
                                     audience of knoxidf-token-multi-hop) and iss KNOXSSO.
  - knoxidf-ldap                  -- mint endpoint for tokens with no aud claim (negative tests).
  - knoxidf-token-multi-hop       -- the multi-hop exchange endpoint: knox.token.audiences=
                                     https://recipient1, its own issuer in both jwt.expected.issuer
                                     and token.exchange.subject.token.policy.audiences.issuers,
                                     delegation.headless.reject.subject.with.act=true,
                                     delegation.max.actor.chain.depth=3, nested act claims and the
                                     'passthrough' audience validator (requested resource -> aud).
  - knoxidf-token-delegation-policy -- single-hop delegation endpoint with the default KNOXSSO
                                     issuer; mints a delegated token from an issuer that is NOT
                                     listed on knoxidf-token-multi-hop.
  - knoxidf-admin                 -- KNOXIDF_ADMIN delegation-policy REST API.

Users: tom is the impersonated subject. recursiveUser and sam are the actors and take turns
on successive hops (recursiveUser -> sam -> recursiveUser -> sam), the same pattern as a
service calling back into its caller. They are the users test_delegation.py already uses, for
the same reason: no other suite mints them through a per-user-limited topology (admin and
guest are minted by test_knoxtoken_jwt under the default per-user limit). Both suites delete
these actors' policies before and after every test.

Rules under test (see knox-site/docs/knoxidf/token_exchange.md, "Multi-hop delegation"):
  - re-exchange by the actor whose subjectAudiences match the token's aud succeeds, for two and
    three hops, and the act chain records every actor in order;
  - a fourth hop exceeds delegation.max.actor.chain.depth=3 -> invalid_request;
  - re-exchange by an actor whose subjectAudiences do not match, or are empty, is rejected by
    policy (the token is bound to its recipient, not to any actor allowed to act for tom);
  - a re-exchanged token from an unlisted issuer is rejected for its missing audience;
  - a re-exchanged token cannot be used in a same-subject or a headless exchange;
  - a delegated token, even one carrying the token-service audience, cannot be a headless
    subject_token (delegation.headless.reject.subject.with.act);
  - actor tokens and first-hop subject tokens still need the token-service audience.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, get_token_claim, knox_get
from delegation_helpers import (
    DelegationPolicyAdmin,
    assert_issued_jwt,
    assert_oauth_error,
    aud_values,
    token_exchange,
)

# ActorIdentity.fromJwt tags every non-k8s-serviceaccount JWT subject with authority "USER".
ACTOR_AUTHORITY = "USER"

# Demo LDAP users (uid == cn); passwords are "<user>-password".
TOM_USER = "tom"
TOM_PASSWORD = "tom-password"
RECURSIVE_USER = "recursiveUser"
RECURSIVE_PASSWORD = "recursiveUser-password"
SAM_USER = "sam"
SAM_PASSWORD = "sam-password"

# The only principal KNOXIDF_ADMIN.acl (admin;*;*) on knoxidf-admin permits.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# knox.token.audiences on knoxidf-token-multi-hop; knoxidf-ldap-aud stamps it on every token.
TOKEN_SERVICE_AUDIENCE = "https://recipient1"
# knoxidf.knox.token.issuer on knoxidf-token-multi-hop, also its only policy-audiences issuer.
MULTI_HOP_ISSUER = "https://localhost:8443/gateway/knoxidf-token-multi-hop"

# One audience per downstream service; each hop requests exactly one (max-one is enforced).
SERVICE_A = "https://multi-hop-service-a"   # sam's service (hop 1 target)
SERVICE_B = "https://multi-hop-service-b"   # recursiveUser's service (hop 2 target)
SERVICE_C = "https://multi-hop-service-c"   # sam's second service (hop 3 target)
SERVICE_D = "https://multi-hop-service-d"   # not addressed to any actor in these tests

MISSING_AUDIENCE = "missing required token audience"
REJECTED_BY_POLICY = "rejected by policy"


class TestMultiHopDelegation(unittest.TestCase):  # pylint: disable=too-many-instance-attributes
    """Multi-hop RFC 8693 delegation with policy-bound subject audiences (KNOX-3520)."""

    def setUp(self):
        base_url = gateway_base_url()
        # Mint endpoints: with the token-service audience, and without any aud claim.
        self.mint_aud_url = base_url + "gateway/knoxidf-ldap-aud/knoxidf/api/v1/token"
        self.mint_no_aud_url = base_url + "gateway/knoxidf-ldap/knoxidf/api/v1/token"
        self.multi_hop_url = base_url + "gateway/knoxidf-token-multi-hop/knoxidf/api/v1/token"
        self.single_hop_url = (
            base_url + "gateway/knoxidf-token-delegation-policy/knoxidf/api/v1/token"
        )
        admin_policy_url = (
            base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/delegation-policies"
        )

        self.tom_auth = HTTPBasicAuth(TOM_USER, TOM_PASSWORD)
        self.recursive_auth = HTTPBasicAuth(RECURSIVE_USER, RECURSIVE_PASSWORD)
        self.sam_auth = HTTPBasicAuth(SAM_USER, SAM_PASSWORD)
        admin_auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)

        self.recursive_policy = DelegationPolicyAdmin(
            self, admin_policy_url, admin_auth, (ACTOR_AUTHORITY, RECURSIVE_USER))
        self.sam_policy = DelegationPolicyAdmin(
            self, admin_policy_url, admin_auth, (ACTOR_AUTHORITY, SAM_USER))
        for policy_admin in (self.recursive_policy, self.sam_policy):
            policy_admin.delete_if_present()
            self.addCleanup(policy_admin.delete_if_present)

    def _mint(self, auth, url=None):
        """Mint a Knox JWT for the user; by default it carries the token-service audience."""
        response = knox_get(url or self.mint_aud_url, auth=auth)
        self.assertEqual(
            response.status_code, 200,
            msg=f"token minting failed: {response.status_code} {response.text}")
        access_token = response.json().get("access_token")
        self.assertTrue(access_token, "KNOXIDF did not return an access_token")
        return access_token

    def _register_chain_policies(self, sam_subject_audiences=(SERVICE_A,)):
        """Register the policies for the recursiveUser -> sam -> recursiveUser -> sam chain.

        recursiveUser may mint for SERVICE_A (hop 1) and SERVICE_C (hop 3), and re-exchange
        tokens addressed to SERVICE_B. sam may mint for SERVICE_B (hops 2 and 4), and
        re-exchange tokens addressed to ``sam_subject_audiences`` (None omits the field).
        """
        self.recursive_policy.register(
            can_act_for_users=[TOM_USER],
            subject_audiences=[SERVICE_B],
            resource_policy={SERVICE_A: [], SERVICE_C: []})
        self.sam_policy.register(
            can_act_for_users=[TOM_USER],
            subject_audiences=(None if sam_subject_audiences is None
                               else list(sam_subject_audiences)),
            resource_policy={SERVICE_B: []})

    def _exchange(self, subject_token, actor_auth, resource):
        """Delegated exchange on the multi-hop topology with a fresh actor token for actor_auth."""
        return token_exchange(self.multi_hop_url, subject_token, resources=[resource],
                              actor_token=self._mint(actor_auth))

    def _assert_hop(self, response, expected_actors, expected_resource):
        """Assert a 200 exchange whose token is for tom, addressed to expected_resource only,
        issued by the multi-hop topology, and whose act chain is expected_actors (latest first).
        """
        token = assert_issued_jwt(self, response)
        self.assertEqual(get_token_claim(token, "sub"), TOM_USER, response.text)
        self.assertEqual(get_token_claim(token, "iss"), MULTI_HOP_ISSUER, response.text)
        # Only the requested resource: no token-service audience is needed to re-exchange it.
        self.assertEqual(aud_values(token), [expected_resource], response.text)

        act = get_token_claim(token, "act")
        for depth, expected_actor in enumerate(expected_actors, start=1):
            self.assertIsInstance(act, dict, f"expected an act claim at depth {depth}: {act}")
            self.assertEqual(act.get("sub"), expected_actor, f"act depth {depth}: {act}")
            act = act.get("act")
        self.assertIsNone(act, f"act chain is deeper than {len(expected_actors)}: {act}")
        return token

    def _two_hops(self):
        """tom -> recursiveUser -> sam; return the hop-2 token (aud SERVICE_B)."""
        hop1 = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth, SERVICE_A),
            [RECURSIVE_USER], SERVICE_A)
        return self._assert_hop(
            self._exchange(hop1, self.sam_auth, SERVICE_B),
            [SAM_USER, RECURSIVE_USER], SERVICE_B)

    def test_two_hop_delegation_succeeds(self):
        """sam re-exchanges the token addressed to its service; act records both actors."""
        self._register_chain_policies()
        self._two_hops()

    def test_three_hop_delegation_succeeds(self):
        """A third hop by recursiveUser, whose subjectAudiences match the hop-2 token, succeeds."""
        self._register_chain_policies()
        hop2 = self._two_hops()
        self._assert_hop(
            self._exchange(hop2, self.recursive_auth, SERVICE_C),
            [RECURSIVE_USER, SAM_USER, RECURSIVE_USER], SERVICE_C)

    def test_fourth_hop_exceeds_max_actor_chain_depth(self):
        """With every policy check passing, a fourth actor exceeds the configured depth of 3."""
        self._register_chain_policies(sam_subject_audiences=(SERVICE_A, SERVICE_C))
        hop3 = self._assert_hop(
            self._exchange(self._two_hops(), self.recursive_auth, SERVICE_C),
            [RECURSIVE_USER, SAM_USER, RECURSIVE_USER], SERVICE_C)

        response = self._exchange(hop3, self.sam_auth, SERVICE_B)
        assert_oauth_error(self, response, 400, "invalid_request",
                           "would exceed the configured maximum of 3")

    def test_reexchange_rejected_when_subject_audiences_do_not_match(self):
        """sam may act for tom, but the hop-1 token is not addressed to sam's subjectAudiences."""
        self._register_chain_policies(sam_subject_audiences=(SERVICE_D,))
        hop1 = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth, SERVICE_A),
            [RECURSIVE_USER], SERVICE_A)

        response = self._exchange(hop1, self.sam_auth, SERVICE_B)
        assert_oauth_error(self, response, 400, "invalid_request", REJECTED_BY_POLICY)

    def test_reexchange_rejected_when_actor_has_no_subject_audiences(self):
        """A policy without subjectAudiences (the default) never accepts a re-exchanged token."""
        self._register_chain_policies(sam_subject_audiences=None)
        hop1 = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth, SERVICE_A),
            [RECURSIVE_USER], SERVICE_A)

        response = self._exchange(hop1, self.sam_auth, SERVICE_B)
        assert_oauth_error(self, response, 400, "invalid_request", REJECTED_BY_POLICY)

    def test_reexchange_rejected_when_issuer_not_listed(self):
        """A delegated token from an unlisted issuer needs the token-service aud, even if it
        is addressed to the actor's subjectAudiences."""
        self._register_chain_policies()
        # Single-hop topology: KNOXSSO issuer and no knox.token.audiences (no-aud tokens qualify).
        unlisted = assert_issued_jwt(self, token_exchange(
            self.single_hop_url, self._mint(self.tom_auth, self.mint_no_aud_url),
            resources=[SERVICE_A],
            actor_token=self._mint(self.recursive_auth, self.mint_no_aud_url)))
        self.assertNotEqual(get_token_claim(unlisted, "iss"), MULTI_HOP_ISSUER,
                            "precondition: token must come from an unlisted issuer")
        self.assertEqual(aud_values(unlisted), [SERVICE_A],
                         "precondition: token must be addressed to sam's subjectAudiences")

        response = self._exchange(unlisted, self.sam_auth, SERVICE_B)
        assert_oauth_error(self, response, 400, "invalid_request", MISSING_AUDIENCE)

    def test_reexchanged_token_rejected_in_same_subject_exchange(self):
        """No actor_token means no policy to bind to: the token-service audience is required."""
        self._register_chain_policies()
        hop1 = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth, SERVICE_A),
            [RECURSIVE_USER], SERVICE_A)

        response = token_exchange(self.multi_hop_url, hop1)
        assert_oauth_error(self, response, 400, "invalid_request", MISSING_AUDIENCE)

    def test_reexchanged_token_rejected_as_headless_subject(self):
        """A headless subject_token is the acting party, so it needs the token-service aud."""
        self._register_chain_policies()
        hop1 = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth, SERVICE_A),
            [RECURSIVE_USER], SERVICE_A)

        response = token_exchange(self.multi_hop_url, hop1, resources=[SERVICE_B],
                                  requested_subject=SAM_USER)
        assert_oauth_error(self, response, 400, "invalid_request", MISSING_AUDIENCE)

    def test_delegated_token_with_token_service_audience_rejected_as_headless_subject(self):
        """delegation.headless.reject.subject.with.act: a token with an act claim cannot act
        headlessly, even when it carries the token-service audience."""
        self.recursive_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={TOKEN_SERVICE_AUDIENCE: []})
        delegated = self._assert_hop(
            self._exchange(self._mint(self.tom_auth), self.recursive_auth,
                           TOKEN_SERVICE_AUDIENCE),
            [RECURSIVE_USER], TOKEN_SERVICE_AUDIENCE)

        response = token_exchange(self.multi_hop_url, delegated, resources=[SERVICE_B],
                                  requested_subject=SAM_USER)
        assert_oauth_error(self, response, 400, "invalid_request",
                           "A delegated subject_token cannot be used for a headless delegation"
                           " exchange")

    def test_actor_token_without_token_service_audience_rejected(self):
        """Actor tokens are never relaxed: an actor token without the token-service aud fails."""
        self._register_chain_policies()
        response = token_exchange(
            self.multi_hop_url, self._mint(self.tom_auth), resources=[SERVICE_A],
            actor_token=self._mint(self.recursive_auth, self.mint_no_aud_url))
        assert_oauth_error(self, response, 400, "invalid_request", MISSING_AUDIENCE)

    def test_first_hop_subject_without_token_service_audience_rejected(self):
        """A subject_token from an unlisted issuer (KNOXSSO) needs the token-service aud."""
        self._register_chain_policies()
        response = token_exchange(
            self.multi_hop_url, self._mint(self.tom_auth, self.mint_no_aud_url),
            resources=[SERVICE_A], actor_token=self._mint(self.recursive_auth))
        assert_oauth_error(self, response, 400, "invalid_request", MISSING_AUDIENCE)


if __name__ == "__main__":
    unittest.main()
