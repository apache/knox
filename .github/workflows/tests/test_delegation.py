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

"""End-to-end tests for the RFC 8693 delegation path through a running Knox gateway (KNOX-3476).

Where test_token_exchange.py proves the delegation *gate* and audience enforcement
(both evaluated before any policy lookup), this suite drives the *full* delegation
path against seeded per-actor policies: a policy is registered through the
KNOXIDF_ADMIN REST API (knoxidf-admin topology), then a delegation exchange is
performed on the knoxidf-token-delegation-policy topology and the minted token (or
the RFC 6749 section 5.2 error) is asserted. Both run in the default docker-compose
build; no separate compose stack is needed.

Topologies exercised:
  - knoxidf-ldap                        -- mint endpoint; issues subject/actor tokens
                                           (per-user limit -1, uncapped) with no aud claim.
  - knoxidf-token-delegation-policy     -- delegation.server.enabled=true,
                                           delegation.requested.subject.enabled=true, both
                                           requested-audience enforcement flags, plus
                                           knoxidf.knox.token.enable.delegated.auth=true (so the
                                           minted token carries the nested act claim) and the
                                           'passthrough' audience validator (so the requested
                                           resource is minted as aud). The service TTL is 86400s
                                           so a shorter per-policy TTL is provably what caps the
                                           minted token's lifetime.
  - knoxidf-admin                       -- KNOXIDF_ADMIN delegation-policy REST API; writes to the
                                           gateway-wide embedded H2 policy store the exchange reads.
  - knoxauthtoken                       -- KNOX-AUTH-SERVICE with the k8s destination audience
                                           validator (KNOX-3568).

Actors and impersonated subjects are distinct so the act claim is meaningful. From the demo
LDAP: recursiveUser (the acting party; its nested level1-3 groups are irrelevant, as the
policies key on the subject's groups), tom (analyst), sam (analyst + scientist). The acting
party is the actor_token's identity (interactive) or the subject_token's own identity
(headless); the impersonated party is the subject_token's subject (interactive) or
requested_subject (headless).

Token footprint: the Knox token store is a gateway-wide singleton and this suite runs first
(alphabetical order). recursiveUser and sam are the actors and tom the subject precisely
because no other suite mints those users through a per-user-limited topology, so the tokens
this suite leaves behind cannot exhaust another user's quota -- notably guest's, which a later
suite (test_knoxtoken_jwt) mints against the default per-user limit. The suite deliberately
does not revoke the tokens it mints: in production, token management is not enabled for tokens
involved in RFC 8693 exchanges, so these tests must stay correct without relying on it.

Acceptance criteria (KNOX-3476); denials are RFC 8693 invalid_request, and scope (AC10)
is deferred until scope support lands:
  AC2 -- user-based policy: exchange succeeds with the expected sub / act / aud / iss.
  AC3 -- group-based policy: subject in an allowed LDAP group succeeds.
  AC4 -- multiple actor policies are isolated; cross-actor resource use fails.
  AC5 -- subject not in the required LDAP group -> invalid_request.
  AC6 -- actor with no registered policy -> invalid_request.
  AC7 -- requesting a resource not in the policy -> invalid_request.
  AC8 -- a policy max TTL shorter than the service TTL caps the minted token's lifetime.
  AC9 -- headless delegation succeeds when the policy allows it, fails (invalid_request) when not.

KNOX-3568 -- audit of delegated token use: accepted, rejected and two-hop delegated tokens.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, get_token_claim, get_token_id_display_text, knox_get
from delegated_audit_helpers import (
    DESTINATION_PATH,
    assert_token_not_in_audit_log,
    audit_records_for,
    call_destination,
    records_with_action,
)
from delegation_helpers import (
    ISSUED_TOKEN_TYPE_JWT,
    DelegationPolicyAdmin,
    assert_oauth_error,
    aud_values,
    token_exchange,
)

# ActorIdentity.fromJwt tags every non-k8s-serviceaccount JWT subject with authority "USER".
ACTOR_AUTHORITY = "USER"

# Demo LDAP users (uid == cn); passwords are "<user>-password". recursiveUser is the acting
# party (its nested level1-3 groups are irrelevant here, since the policies key on the
# subject's groups); tom is in analyst only, sam is in analyst and scientist. recursiveUser
# is used as the actor instead of guest so this first-running suite adds nothing to guest's
# shared, per-user-limited token count (see the module docstring for why that matters).
ACTOR_USER = "recursiveUser"
ACTOR_PASSWORD = "recursiveUser-password"
TOM_USER = "tom"
TOM_PASSWORD = "tom-password"
SAM_USER = "sam"
SAM_PASSWORD = "sam-password"

# The only principal KNOXIDF_ADMIN.acl (admin;*;*) on knoxidf-admin permits.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# LDAP groups used by the group-based policy tests.
ANALYST_GROUP = "analyst"      # tom and sam
SCIENTIST_GROUP = "scientist"  # sam only (tom is deliberately NOT a member)

# Distinct delegated resources; each request carries exactly one (the topology enforces max-one).
RESOURCE_A = "https://delegated-resource-a"
RESOURCE_B = "https://delegated-resource-b"

DESTINATION_SERVICE = "secret-service.platform.svc.cluster.local"
DESTINATION_RESOURCE = "https://" + DESTINATION_SERVICE
OTHER_DESTINATION_SERVICE = "other-service.platform.svc.cluster.local"

# knoxidf.knox.token.ttl on the topology is 86400000 ms (86400 s). AC8 sets a much shorter
# per-policy TTL so the difference proves the policy value, not the service default, applied.
SERVICE_TTL_SEC = 86400
POLICY_TTL_SEC = 120


class TestDelegation(unittest.TestCase):  # pylint: disable=too-many-instance-attributes
    """Full RFC 8693 delegation path (policy lookup, act/aud minting, TTL, headless) through Knox.

    Policies live in the gateway-wide embedded H2 store, so a test seeds the policies it needs,
    then removes them; setUp clears both actors (recursiveUser, sam) up front and registers the same
    cleanup so a prior run or a failed test cannot leak a policy into the next.
    """

    def setUp(self):
        base_url = gateway_base_url()
        # Mint endpoint (uncapped) used to obtain genuine Knox JWTs for actor and subject.
        self.mint_url = base_url + "gateway/knoxidf-ldap/knoxidf/api/v1/token"
        # Delegation exchange endpoint that mints act + aud and allows headless exchange.
        self.delegation_url = (
            base_url + "gateway/knoxidf-token-delegation-policy/knoxidf/api/v1/token"
        )
        # Delegation-policy admin REST API (gateway-wide singleton store).
        self.admin_policy_url = (
            base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/delegation-policies"
        )

        self.actor_auth = HTTPBasicAuth(ACTOR_USER, ACTOR_PASSWORD)
        self.tom_auth = HTTPBasicAuth(TOM_USER, TOM_PASSWORD)
        self.sam_auth = HTTPBasicAuth(SAM_USER, SAM_PASSWORD)
        self.admin_auth = HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)

        # One admin client per actor whose policy a test manages.
        self._actor_policy = DelegationPolicyAdmin(
            self, self.admin_policy_url, self.admin_auth, (ACTOR_AUTHORITY, ACTOR_USER))
        self._sam_policy = DelegationPolicyAdmin(
            self, self.admin_policy_url, self.admin_auth, (ACTOR_AUTHORITY, SAM_USER))

        # Known-clean start and guaranteed teardown for every actor this suite touches.
        for policy_admin in (self._actor_policy, self._sam_policy):
            policy_admin.delete_if_present()
            self.addCleanup(policy_admin.delete_if_present)

    def _mint(self, auth):
        """Mint and return a genuine Knox JWT for the Basic-authenticated user."""
        response = knox_get(self.mint_url, auth=auth)
        self.assertEqual(
            response.status_code, 200,
            msg=f"token minting failed: {response.status_code} {response.text}")
        access_token = response.json().get("access_token")
        self.assertTrue(access_token, "KNOXTOKEN did not return an access_token")
        return access_token

    def _assert_delegated_token(
            self, response, expected_subject, expected_actor, expected_resource):
        """Assert a 200 delegation exchange whose token records the impersonation end to end."""
        self.assertEqual(response.status_code, 200, response.text)
        body = response.json()
        token = body.get("access_token")
        self.assertTrue(token, "delegation exchange did not return an access_token")
        self.assertEqual(body.get("issued_token_type"), ISSUED_TOKEN_TYPE_JWT, response.text)
        # sub is the impersonated party; the nested act claim records the acting party.
        self.assertEqual(get_token_claim(token, "sub"), expected_subject, response.text)
        act = get_token_claim(token, "act")
        self.assertIsInstance(act, dict, f"expected a nested act claim, got: {act}")
        self.assertEqual(act.get("sub"), expected_actor, response.text)
        # The requested resource is minted as the token's audience (passthrough validator).
        self.assertIn(expected_resource, aud_values(token), response.text)
        # The gateway signs and stamps its issuer on the minted token.
        self.assertTrue(get_token_claim(token, "iss"), "minted token has no iss claim")
        return token

    def _delegate_tom_to_destination(self, subject_token, actor_token, actor):
        """Exchange for a token delegated to ``actor`` acting for tom on DESTINATION_RESOURCE."""
        response = token_exchange(self.delegation_url, subject_token,
                                  resources=[DESTINATION_RESOURCE], actor_token=actor_token)
        return self._assert_delegated_token(response, TOM_USER, actor, DESTINATION_RESOURCE)

    def _assert_audience_check(self, records, token, act_chain, destination_service):
        """Assert the fields of the request's single audience-validation record and return it."""
        checks = records_with_action(records, "audience-validation")
        self.assertEqual(len(checks), 1, f"expected one audience-validation record: {records}")
        check = checks[0]
        fields = check.fields
        self.assertEqual(fields.get("event_type"), "delegated_audience_check", check.line)
        self.assertEqual(fields.get("claims_verified"), "false", check.line)
        self.assertEqual(fields.get("token_id"),
                         get_token_id_display_text(get_token_claim(token, "knox.id")), check.line)
        self.assertEqual(fields.get("sub"), TOM_USER, check.line)
        self.assertEqual(fields.get("act_chain"), act_chain, check.line)
        self.assertEqual(fields.get("dest_service"), destination_service, check.line)
        self.assertEqual(fields.get("path"), DESTINATION_PATH, check.line)
        self.assertEqual(check.resource, DESTINATION_PATH, check.line)
        return check

    def _assert_accepted_at_destination(self, token, act_chain):
        """Present ``token`` for DESTINATION_SERVICE and assert the request's success records."""
        response, uri = call_destination(token, DESTINATION_SERVICE)
        self.assertEqual(response.status_code, 200, response.text)

        records = audit_records_for(self, uri)
        check = self._assert_audience_check(records, token, act_chain, DESTINATION_SERVICE)
        self.assertEqual(check.outcome, "success", check.line)
        self.assertNotIn("reason", check.fields, check.line)

        authentications = records_with_action(records, "authentication")
        self.assertEqual(len(authentications), 1, f"expected one authentication record: {records}")
        authentication = authentications[0]
        self.assertEqual(authentication.outcome, "success", authentication.line)
        self.assertEqual(authentication.user, TOM_USER, authentication.line)
        self.assertEqual(authentication.request_id, check.request_id, authentication.line)
        self.assertEqual(
            authentication.fields,
            {"act_chain": act_chain, "dest_service": DESTINATION_SERVICE, "path": DESTINATION_PATH},
            authentication.line)
        assert_token_not_in_audit_log(self, token)

    def test_ac2_user_policy_delegation_succeeds(self):
        """AC2: a user-based policy authorizes delegation; token carries sub, act, aud, iss."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={RESOURCE_A: []})

        subject_token = self._mint(self.tom_auth)   # impersonated party
        actor_token = self._mint(self.actor_auth)   # acting party

        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_A], actor_token=actor_token)
        self._assert_delegated_token(response, TOM_USER, ACTOR_USER, RESOURCE_A)

    def test_ac3_group_policy_delegation_succeeds(self):
        """AC3: a group-based policy lets an actor act for any user in the allowed group."""
        # No explicit user grant: authorization must come solely from tom's analyst membership.
        self._actor_policy.register(
            can_act_for_groups=[ANALYST_GROUP], resource_policy={RESOURCE_A: []})

        subject_token = self._mint(self.tom_auth)    # tom is in the analyst group
        actor_token = self._mint(self.actor_auth)

        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_A], actor_token=actor_token)
        self._assert_delegated_token(response, TOM_USER, ACTOR_USER, RESOURCE_A)

    def test_ac4_multiple_actor_policies_are_isolated(self):
        """AC4: each actor reaches only its own policy's resource; cross-actor use is denied."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={RESOURCE_A: []})
        self._sam_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={RESOURCE_B: []})

        subject_token = self._mint(self.tom_auth)
        primary_actor = self._mint(self.actor_auth)
        sam_actor = self._mint(self.sam_auth)

        # Each actor may reach the resource its own policy allows.
        self._assert_delegated_token(
            token_exchange(self.delegation_url, subject_token,
                           resources=[RESOURCE_A], actor_token=primary_actor),
            TOM_USER, ACTOR_USER, RESOURCE_A)
        self._assert_delegated_token(
            token_exchange(self.delegation_url, subject_token,
                           resources=[RESOURCE_B], actor_token=sam_actor),
            TOM_USER, SAM_USER, RESOURCE_B)

        # Cross-actor use fails: the primary actor's policy does not allow RESOURCE_B,
        # sam's does not allow RESOURCE_A.
        assert_oauth_error(
            self, token_exchange(self.delegation_url, subject_token,
                                 resources=[RESOURCE_B], actor_token=primary_actor),
            400, "invalid_request", "rejected by policy")
        assert_oauth_error(
            self, token_exchange(self.delegation_url, subject_token,
                                 resources=[RESOURCE_A], actor_token=sam_actor),
            400, "invalid_request", "rejected by policy")

    def test_ac5_subject_not_in_required_group_rejected(self):
        """AC5: a group-based policy denies an actor acting for a user outside the allowed group."""
        # tom is NOT in the scientist group, so this policy must not authorize acting for tom.
        self._actor_policy.register(
            can_act_for_groups=[SCIENTIST_GROUP], resource_policy={RESOURCE_A: []})

        subject_token = self._mint(self.tom_auth)
        actor_token = self._mint(self.actor_auth)

        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_A], actor_token=actor_token)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")

    def test_ac6_actor_with_no_policy_rejected(self):
        """AC6: an actor with no registered policy cannot perform a delegation exchange."""
        # setUp already cleared this actor's policy; be explicit that none is registered.
        self._actor_policy.delete_if_present()

        subject_token = self._mint(self.tom_auth)
        actor_token = self._mint(self.actor_auth)

        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_A], actor_token=actor_token)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")

    def test_ac7_resource_not_in_policy_rejected(self):
        """AC7: requesting a resource the policy does not grant is denied."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={RESOURCE_A: []})

        subject_token = self._mint(self.tom_auth)
        actor_token = self._mint(self.actor_auth)

        # RESOURCE_B is not in the policy's resourcePolicy (only RESOURCE_A is).
        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_B], actor_token=actor_token)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")

    def test_ac8_policy_ttl_caps_token_lifetime(self):
        """AC8: a policy max TTL shorter than the service TTL caps the minted token's lifetime."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={RESOURCE_A: []},
            token_ttl_sec=POLICY_TTL_SEC)

        subject_token = self._mint(self.tom_auth)
        actor_token = self._mint(self.actor_auth)

        response = token_exchange(
            self.delegation_url, subject_token, resources=[RESOURCE_A], actor_token=actor_token)
        token = self._assert_delegated_token(response, TOM_USER, ACTOR_USER, RESOURCE_A)

        issued_at = get_token_claim(token, "iat")
        expires_at = get_token_claim(token, "exp")
        self.assertIsNotNone(issued_at, "minted token has no iat claim")
        self.assertIsNotNone(expires_at, "minted token has no exp claim")
        lifetime_sec = expires_at - issued_at
        # At or below the policy max...
        self.assertGreater(lifetime_sec, 0, "minted token lifetime must be positive")
        self.assertLessEqual(
            lifetime_sec, POLICY_TTL_SEC,
            f"lifetime {lifetime_sec}s exceeds the policy max {POLICY_TTL_SEC}s")
        # ...and far under the service TTL, proving the policy value is what applied.
        self.assertLess(
            lifetime_sec, SERVICE_TTL_SEC,
            f"lifetime {lifetime_sec}s was not capped below the service TTL {SERVICE_TTL_SEC}s")

    def test_ac9_headless_delegation_succeeds_when_allowed(self):
        """AC9: headless delegation (requested_subject, no actor_token) succeeds when allowed."""
        self._actor_policy.register(
            allow_headless_exchange=True, can_act_for_users=[TOM_USER],
            resource_policy={RESOURCE_A: []})

        # The subject_token's own identity (recursiveUser) is the acting party; tom is impersonated.
        actor_token = self._mint(self.actor_auth)
        response = token_exchange(
            self.delegation_url, actor_token, resources=[RESOURCE_A], requested_subject=TOM_USER)
        self._assert_delegated_token(response, TOM_USER, ACTOR_USER, RESOURCE_A)

    def test_ac9_headless_delegation_rejected_when_not_allowed(self):
        """AC9: the same headless request is denied when the policy forbids headless exchange."""
        # Identical grant to the success case except headless is not permitted.
        self._actor_policy.register(
            allow_headless_exchange=False, can_act_for_users=[TOM_USER],
            resource_policy={RESOURCE_A: []})

        actor_token = self._mint(self.actor_auth)
        response = token_exchange(
            self.delegation_url, actor_token, resources=[RESOURCE_A], requested_subject=TOM_USER)
        assert_oauth_error(self, response, 400, "invalid_request", "rejected by policy")

    def test_delegated_token_accepted_at_destination_is_audited(self):
        """KNOX-3568: accepting a delegated token at its destination is audited."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={DESTINATION_RESOURCE: []})
        token = self._delegate_tom_to_destination(
            self._mint(self.tom_auth), self._mint(self.actor_auth), ACTOR_USER)

        self._assert_accepted_at_destination(token, ACTOR_USER)

    def test_delegated_token_rejected_at_destination_is_audited(self):
        """KNOX-3568: rejecting a delegated token meant for another destination is audited."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={DESTINATION_RESOURCE: []})
        token = self._delegate_tom_to_destination(
            self._mint(self.tom_auth), self._mint(self.actor_auth), ACTOR_USER)

        # The token's aud names DESTINATION_SERVICE, not the destination the request is for.
        response, uri = call_destination(token, OTHER_DESTINATION_SERVICE)
        self.assertEqual(response.status_code, 400, response.text)

        records = audit_records_for(self, uri)
        check = self._assert_audience_check(records, token, ACTOR_USER, OTHER_DESTINATION_SERVICE)
        self.assertEqual(check.outcome, "failure", check.line)
        self.assertEqual(check.fields.get("reason"), "audience_invalid", check.line)
        self.assertTrue(check.fields.get("detail"), f"expected a failure detail: {check.line}")

        authentications = records_with_action(records, "authentication")
        self.assertEqual(len(authentications), 1, f"expected one authentication record: {records}")
        rejection = authentications[0]
        self.assertEqual(rejection.outcome, "failure", rejection.line)
        self.assertEqual(rejection.request_id, check.request_id, rejection.line)
        self.assertEqual(rejection.fields, {
            "event_type": "delegated_request_rejected",
            "claims_verified": "false",
            "token_id": check.fields.get("token_id"),
            "sub": TOM_USER,
            "act_chain": ACTOR_USER,
            "dest_service": OTHER_DESTINATION_SERVICE,
            "path": DESTINATION_PATH,
            "reason": "audience_invalid",
            "detail": check.fields.get("detail"),
        }, rejection.line)
        assert_token_not_in_audit_log(self, token)

    def test_nested_act_chain_is_audited_most_recent_actor_first(self):
        """KNOX-3568: a token delegated twice is audited with its whole act chain."""
        self._actor_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={DESTINATION_RESOURCE: []})
        self._sam_policy.register(
            can_act_for_users=[TOM_USER], resource_policy={DESTINATION_RESOURCE: []})

        # recursiveUser acts for tom; sam then acts for tom using recursiveUser's delegated token.
        first_hop = self._delegate_tom_to_destination(
            self._mint(self.tom_auth), self._mint(self.actor_auth), ACTOR_USER)
        second_hop = self._delegate_tom_to_destination(
            first_hop, self._mint(self.sam_auth), SAM_USER)
        self.assertEqual(get_token_claim(second_hop, "act"),
                         {"sub": SAM_USER, "act": {"sub": ACTOR_USER}})

        self._assert_accepted_at_destination(second_hop, f"{SAM_USER}<-{ACTOR_USER}")


if __name__ == "__main__":
    unittest.main()
