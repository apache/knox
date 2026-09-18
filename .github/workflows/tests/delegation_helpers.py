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

"""Shared RFC 8693 delegation helpers for Knox gateway integration tests.

Extracted from test_token_exchange.py so that both that suite and the delegation
end-to-end suite (test_delegation.py) drive the JWTProvider token-exchange
endpoint and the KNOXIDF_ADMIN delegation-policy REST API through a single
implementation. Two concerns live here:

  - token_exchange / assert_oauth_error -- build an RFC 8693 exchange request
    (same-subject, interactive delegation via actor_token, or headless delegation
    via requested_subject) and assert an RFC 6749 section 5.2 style error body.
  - DelegationPolicyAdmin -- a small client for the delegation-policy admin API,
    scoped to a single (actorAuthority, actorId) actor so a test needing policies
    for several actors creates one instance per actor.
"""

from __future__ import annotations

from common_utils import get_token_claim, knox_delete, knox_get, knox_post, knox_put

# RFC 8693 token-exchange identifiers.
TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
JWT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"
# KNOXIDF always mints a JWT, so the exchange response advertises the JWT URN.
ISSUED_TOKEN_TYPE_JWT = "urn:ietf:params:oauth:token-type:jwt"


def aud_values(token):
    """Return a token's aud claim as a list (JWT aud may serialize as a string or a list)."""
    aud = get_token_claim(token, "aud")
    if aud is None:
        return []
    return aud if isinstance(aud, list) else [aud]


def token_exchange(url, subject_token, resources=None, actor_token=None,
                   requested_subject=None):
    """POST an RFC 8693 token exchange, returning the raw response.

    ``resources`` is conveyed as (possibly repeated) ``resource`` form values. An
    ``actor_token`` (sent with its required type) makes the request an interactive
    delegation exchange in which the actor_token is the acting party and the
    subject_token is the impersonated party. A ``requested_subject`` that differs
    from the subject_token's own subject makes it a headless delegation exchange in
    which the subject_token itself is the acting party.
    """
    data = [
        ("grant_type", TOKEN_EXCHANGE_GRANT),
        ("subject_token", subject_token),
        ("subject_token_type", JWT_TOKEN_TYPE),
    ]
    if actor_token is not None:
        data.append(("actor_token", actor_token))
        data.append(("actor_token_type", JWT_TOKEN_TYPE))
    if requested_subject is not None:
        data.append(("requested_subject", requested_subject))
    for resource in resources or []:
        data.append(("resource", resource))
    return knox_post(url, data=data)


def assert_oauth_error(testcase, response, expected_status, expected_error, message_substring):
    """Assert an RFC 6749 section 5.2 style JSON OAuth error with the expected fields."""
    testcase.assertEqual(
        response.status_code,
        expected_status,
        msg=f"unexpected status: {response.status_code} {response.text}",
    )
    try:
        body = response.json()
    except ValueError:
        body = {}
    testcase.assertEqual(body.get("error"), expected_error, response.text)
    description = body.get("error_description", "")
    testcase.assertIn(message_substring, description, response.text)


class DelegationPolicyAdmin:
    """Client for the KNOXIDF_ADMIN delegation-policy REST API, scoped to one actor.

    Each instance manages the single delegation policy for one actor -- an
    ``(actorAuthority, actorId)`` pair -- in the gateway-wide policy store, so a test
    that needs policies for several actors (e.g. cross-actor isolation) creates one
    instance per actor. Assertions are delegated to the owning ``unittest.TestCase``
    so a failed admin call is reported at the call site.
    """

    # The policy body has many independent, all-optional fields; surfacing each as its own
    # keyword argument reads far better at call sites than a single opaque dict would.
    # pylint: disable=too-many-arguments

    def __init__(self, testcase, admin_policy_url, admin_auth, actor):
        self._testcase = testcase
        self._admin_policy_url = admin_policy_url
        self._admin_auth = admin_auth
        self._actor_authority, self._actor_id = actor

    def find_registration_id(self):
        """Return the registrationId of this actor's policy, or None if none is registered."""
        response = knox_get(
            self._admin_policy_url,
            params={"actorAuthority": self._actor_authority},
            auth=self._admin_auth,
        )
        self._testcase.assertEqual(response.status_code, 200, response.text)
        for policy in response.json().get("policies", []):
            if policy.get("actorId") == self._actor_id:
                return policy.get("registrationId")
        return None

    def delete_if_present(self):
        """Best-effort cleanup: remove this actor's policy if one exists (idempotent)."""
        registration_id = self.find_registration_id()
        if registration_id is not None:
            knox_delete(self._admin_policy_url + "/" + registration_id, auth=self._admin_auth)

    def _body(self, *, status, can_act_for_users, can_act_for_groups, resource_policy,
              allow_headless_exchange, token_ttl_sec):
        """Build a policy request body for this actor, omitting fields left as None."""
        body = {
            "actorAuthority": self._actor_authority,
            "actorId": self._actor_id,
            "status": status,
        }
        if can_act_for_users is not None:
            body["canActForUsers"] = can_act_for_users
        if can_act_for_groups is not None:
            body["canActForGroups"] = can_act_for_groups
        if resource_policy is not None:
            body["resourcePolicy"] = resource_policy
        if allow_headless_exchange is not None:
            body["allowHeadlessExchange"] = allow_headless_exchange
        if token_ttl_sec is not None:
            body["tokenTtlSec"] = token_ttl_sec
        return body

    def register(self, *, status="active", can_act_for_users=None, can_act_for_groups=None,
                 resource_policy=None, allow_headless_exchange=None, token_ttl_sec=None):
        """POST a fresh policy for this actor; assert 201 and return its registrationId."""
        response = knox_post(
            self._admin_policy_url,
            json=self._body(
                status=status, can_act_for_users=can_act_for_users,
                can_act_for_groups=can_act_for_groups, resource_policy=resource_policy,
                allow_headless_exchange=allow_headless_exchange, token_ttl_sec=token_ttl_sec),
            auth=self._admin_auth,
        )
        self._testcase.assertEqual(response.status_code, 201, response.text)
        return response.json()["registrationId"]

    def update(self, registration_id, *, status="active", can_act_for_users=None,
               can_act_for_groups=None, resource_policy=None, allow_headless_exchange=None,
               token_ttl_sec=None):
        """PUT a full-replace update of this actor's policy; assert 200 and return the body.

        Identity fields (actorAuthority/actorId/createdBy/createdAt) are immutable
        server-side, so this updates the same row rather than creating a new one.
        """
        response = knox_put(
            self._admin_policy_url + "/" + registration_id,
            json=self._body(
                status=status, can_act_for_users=can_act_for_users,
                can_act_for_groups=can_act_for_groups, resource_policy=resource_policy,
                allow_headless_exchange=allow_headless_exchange, token_ttl_sec=token_ttl_sec),
            auth=self._admin_auth,
        )
        self._testcase.assertEqual(response.status_code, 200, response.text)
        return response.json()
