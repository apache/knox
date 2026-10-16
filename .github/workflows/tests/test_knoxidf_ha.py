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

"""End-to-end HA tests for the KnoxIDF registries shared by two gateways.

The KnoxIDF HA compose override (docker-compose.knoxidf-ha.yml) runs two gateways,
knox (node A) and knox2 (node B), on one shared PostgreSQL database. Every change in
these tests is made through node A's KNOXIDF_ADMIN API, and every check runs against
node B, so each check proves that a change reached the other node rather than only the
node that made it.

  - Trusted OIDC issuers: every node serves the registry from an in-memory snapshot
    that it re-reads from the database within
    gateway.trusted.oidc.issuer.registry.cache.ttl.secs (5s in knoxidf-ha/gateway-site.xml).
    A registration, a deregistration and a re-registration made on node A therefore
    show up on node B within that TTL; the checks poll node B for a few TTLs.
  - Delegation policies are read from the database on every exchange, so a policy
    change made on node A applies to the very next exchange on node B, without polling.

The suite is skipped unless KNOX_GATEWAY_URL_2 names the second gateway.
"""

import os
import time
import unittest

import requests
from requests.auth import HTTPBasicAuth

from common_utils import get_token_claim, knox_delete, knox_get, knox_post
from delegation_helpers import DelegationPolicyAdmin, assert_oauth_error, token_exchange

# The LDAP 'admin' user is the only principal the knoxidf-admin ACL permits.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# The k3s OIDC issuer, the projected SA token and the k3s serving CA the
# k8s-bootstrap service exports (see docker-compose.yml and test_k8s_delegation.py).
# The CA is not in the JVM truststore, so the issuer is registered with it as its jwksCa.
ISSUER_URL = "https://k3s:6443"
SA_TOKEN_FILE = "/k3s/sa-token"
ISSUER_CA_FILE = "/k3s/k3s-ca.crt"
SA_SUBJECT_PREFIX = "system:serviceaccount:"
K8S_SA_ACTOR_AUTHORITY = "K8S_SA"

TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
SUBJECT_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:jwt"

# Demo LDAP user impersonated by the headless delegation exchange.
IMPERSONATED_USER = "tom"
# The delegation topology requires exactly one absolute-URI resource per exchange.
DELEGATED_RESOURCE = "https://k8s-delegated-resource"

# gateway.trusted.oidc.issuer.registry.cache.ttl.secs in knoxidf-ha/gateway-site.xml.
REGISTRY_TTL_SECS = 5
# A change must reach the other node within one TTL; allow a few for a loaded CI host.
PROPAGATION_TIMEOUT_SECS = 3 * REGISTRY_TTL_SECS
POLL_INTERVAL_SECS = 0.5
# How long to wait for both gateways to deploy the KnoxIDF topologies.
READINESS_TIMEOUT_SECS = 180

ADMIN_ISSUERS_PATH = "gateway/knoxidf-admin/knoxidf/admin/v1/trusted-oidc-issuers"
ADMIN_POLICIES_PATH = "gateway/knoxidf-admin/knoxidf/admin/v1/delegation-policies"
TOKEN_PATH = "gateway/knoxidf-token/knoxidf/api/v1/token"
DELEGATION_TOKEN_PATH = "gateway/knoxidf-token-delegation-policy/knoxidf/api/v1/token"


def _node_url(env_name):
    """Return the gateway base URL in the given environment variable, with a trailing slash."""
    url = os.environ.get(env_name)
    if not url:
        return None
    return url if url.endswith("/") else (url + "/")


def _admin_auth():
    """HTTP Basic credentials for the knoxidf-admin API."""
    return HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)


def _read_k3s_file(path):
    """Return the content of a file the k8s-bootstrap service exported to /k3s."""
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def _wait_until_ready(base_url):
    """Wait until the gateway serves the knoxidf-admin API, or fail the suite."""
    deadline = time.monotonic() + READINESS_TIMEOUT_SECS
    last = "no response"
    while time.monotonic() < deadline:
        try:
            response = knox_get(base_url + ADMIN_ISSUERS_PATH, auth=_admin_auth())
            if response.status_code == 200:
                return
            last = f"HTTP {response.status_code}: {response.text}"
        except requests.exceptions.RequestException as error:
            last = str(error)
        time.sleep(2)
    raise AssertionError(
        f"{base_url} did not serve the knoxidf-admin API within "
        f"{READINESS_TIMEOUT_SECS}s; last result: {last}")


class TestKnoxIdfHa(unittest.TestCase):
    """KnoxIDF registry changes made on one gateway reach the other gateway."""

    node_a = None
    node_b = None

    @classmethod
    def setUpClass(cls):
        cls.node_a = _node_url("KNOX_GATEWAY_URL")
        cls.node_b = _node_url("KNOX_GATEWAY_URL_2")
        if not cls.node_a or not cls.node_b:
            raise unittest.SkipTest(
                "KNOX_GATEWAY_URL_2 is not set; the HA suite needs two gateways")
        for base_url in (cls.node_a, cls.node_b):
            _wait_until_ready(base_url)

    def setUp(self):
        try:
            self.sa_token = _read_k3s_file(SA_TOKEN_FILE).strip()
            self.issuer_ca = _read_k3s_file(ISSUER_CA_FILE)
        except OSError as error:
            self.skipTest(f"k3s SA token or CA not available: {error}")
        # Start from an empty registry on both nodes, and leave it empty afterwards.
        self._remove_issuer_on_a()
        self.addCleanup(self._remove_issuer_on_a)
        self._wait_for(lambda: ISSUER_URL not in self._issuer_urls(self.node_b),
                       "node B to drop the issuer left over from a previous test")

    # -- helpers ---------------------------------------------------------------------

    def _register_issuer_on_a(self):
        """Register the k3s issuer through node A (dynamic JWKS, trusting the k3s CA)."""
        response = knox_post(
            self.node_a + ADMIN_ISSUERS_PATH,
            json={"issuerUrl": ISSUER_URL, "dynamicJwks": True, "clusterName": "k3s",
                  "jwksCa": self.issuer_ca},
            auth=_admin_auth(),
        )
        self.assertEqual(response.status_code, 201, response.text)

    def _remove_issuer_on_a(self):
        """Deregister the k3s issuer through node A (idempotent: 204 either way)."""
        response = knox_delete(
            self.node_a + ADMIN_ISSUERS_PATH, params={"issuerUrl": ISSUER_URL},
            auth=_admin_auth())
        self.assertEqual(response.status_code, 204, response.text)

    def _issuer_urls(self, base_url):
        """Return the issuerUrl values in the given node's registry snapshot."""
        response = knox_get(base_url + ADMIN_ISSUERS_PATH, auth=_admin_auth())
        self.assertEqual(response.status_code, 200, response.text)
        return [entry.get("issuerUrl") for entry in response.json()]

    def _exchange_on_b(self):
        """Perform a same-subject exchange of the real SA token on node B."""
        return knox_post(
            self.node_b + TOKEN_PATH,
            data={
                "grant_type": TOKEN_EXCHANGE_GRANT,
                "subject_token": self.sa_token,
                "subject_token_type": SUBJECT_TOKEN_TYPE,
            },
        )

    def _wait_for(self, condition, description):
        """Poll condition until it holds, failing after PROPAGATION_TIMEOUT_SECS.

        Returns how long the condition took to hold, in seconds.
        """
        start = time.monotonic()
        deadline = start + PROPAGATION_TIMEOUT_SECS
        while True:
            if condition():
                return time.monotonic() - start
            if time.monotonic() >= deadline:
                self.fail(f"timed out after {PROPAGATION_TIMEOUT_SECS}s waiting for {description} "
                          f"(registry TTL is {REGISTRY_TTL_SECS}s)")
            time.sleep(POLL_INTERVAL_SECS)

    def _wait_until_b_accepts(self, description):
        """Wait until node B trusts the k3s issuer, then return the successful exchange."""
        result = {}

        def accepted():
            result["response"] = self._exchange_on_b()
            return result["response"].status_code == 200

        self._wait_for(accepted, description)
        return result["response"]

    def _wait_until_b_rejects(self, description):
        """Wait until node B no longer trusts the k3s issuer, then return the rejection."""
        result = {}

        def rejected():
            result["response"] = self._exchange_on_b()
            return result["response"].status_code == 401

        self._wait_for(rejected, description)
        return result["response"]

    # -- trusted OIDC issuer registry --------------------------------------------------

    def test_issuer_registered_on_one_node_is_trusted_on_the_other(self):
        """A registration made on node A is listed and trusted by node B within the TTL."""
        # Node B does not trust the issuer before it is registered.
        self.assertEqual(self._exchange_on_b().status_code, 401)

        self._register_issuer_on_a()
        # Node A reads its own write at once, without waiting for its TTL.
        self.assertIn(ISSUER_URL, self._issuer_urls(self.node_a))

        self._wait_for(lambda: ISSUER_URL in self._issuer_urls(self.node_b),
                       "node B to list the issuer registered on node A")
        exchanged = self._wait_until_b_accepts(
            "node B to accept a token from the issuer registered on node A")
        self.assertIn("access_token", exchanged.json())

    def test_issuer_deregistered_on_one_node_is_rejected_on_the_other(self):
        """A deregistration made on node A stops node B trusting the issuer within the TTL,
        and a later re-registration on node A is trusted by node B again."""
        self._register_issuer_on_a()
        self._wait_until_b_accepts("node B to accept a token from the issuer registered on node A")

        self._remove_issuer_on_a()
        # Node A stops trusting the issuer at once.
        self.assertNotIn(ISSUER_URL, self._issuer_urls(self.node_a))
        rejected = self._wait_until_b_rejects(
            "node B to reject a token from the issuer deregistered on node A")
        self.assertIn("invalid_request", rejected.text)
        self.assertNotIn(ISSUER_URL, self._issuer_urls(self.node_b))

        self._register_issuer_on_a()
        self._wait_until_b_accepts(
            "node B to accept a token from the issuer re-registered on node A")

    # -- delegation policies -----------------------------------------------------------

    def _sa_actor_id(self):
        """The delegation-policy actorId of the SA token: "<iss>:<namespace>:<sa-name>"."""
        issuer = get_token_claim(self.sa_token, "iss")
        subject = get_token_claim(self.sa_token, "sub")
        return issuer + ":" + subject[len(SA_SUBJECT_PREFIX):]

    def _headless_exchange_on_b(self):
        """Headless delegation on node B: the SA token acts for the impersonated user."""
        return token_exchange(
            self.node_b + DELEGATION_TOKEN_PATH, self.sa_token,
            resources=[DELEGATED_RESOURCE], requested_subject=IMPERSONATED_USER)

    def test_delegation_policy_changed_on_one_node_applies_on_the_other(self):
        """Delegation policies are not cached: a policy registered or updated on node A
        applies to the very next exchange on node B."""
        self._register_issuer_on_a()
        self._wait_until_b_accepts("node B to accept a token from the issuer registered on node A")

        policy_admin = DelegationPolicyAdmin(
            self, self.node_a + ADMIN_POLICIES_PATH, _admin_auth(),
            (K8S_SA_ACTOR_AUTHORITY, self._sa_actor_id()))
        policy_admin.delete_if_present()
        self.addCleanup(policy_admin.delete_if_present)

        registration_id = policy_admin.register(
            allow_headless_exchange=True, can_act_for_users=[IMPERSONATED_USER],
            resource_policy={DELEGATED_RESOURCE: []})
        allowed = self._headless_exchange_on_b()
        self.assertEqual(allowed.status_code, 200, allowed.text)
        minted = allowed.json()["access_token"]
        self.assertEqual(get_token_claim(minted, "sub"), IMPERSONATED_USER, allowed.text)

        # Same grant with headless exchange forbidden, so that flag is the only cause of the denial.
        policy_admin.update(
            registration_id, allow_headless_exchange=False,
            can_act_for_users=[IMPERSONATED_USER], resource_policy={DELEGATED_RESOURCE: []})
        assert_oauth_error(self, self._headless_exchange_on_b(), 400, "invalid_request",
                           "rejected by policy")


if __name__ == "__main__":
    unittest.main()
