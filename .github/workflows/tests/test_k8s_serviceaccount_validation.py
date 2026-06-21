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

"""Integration tests for the k8s pre-auth ServiceAccountValidator.

These exercise the 'k8sauth' topology (HeaderPreAuth federation backed by the
k8s ServiceAccountValidator) against a throwaway k3s cluster. The cluster has a
ServiceAccount 'test-sa' in namespace 'test' annotated with
'knox.apache.org/owner-username: bob'. The validator parses the SPIFFE id, looks
up that ServiceAccount, and only lets the request through when the annotation
value equals the asserted user from the x-knoxidf-obo.username header.
"""

import unittest

from common_utils import gateway_base_url, knox_get

# A request is authenticated only when both headers are present, the SPIFFE id
# parses to namespace 'test' / service account 'test-sa', and the asserted user
# matches the ServiceAccount's owner-username annotation ("bob").
SPIFFE_HEADER = "x-spiffe-id"
USER_HEADER = "x-knoxidf-obo.username"
VALID_SPIFFE_ID = "spiffe://cluster.local/ns/test/sa/test-sa"


class TestK8sServiceAccountValidation(unittest.TestCase):
    """SPIFFE id to ServiceAccount annotation matching via the k8sauth topology."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # 'ping' always returns 200/OK once auth passes, so it isolates the
        # auth outcome from gateway readiness (unlike 'gateway-status').
        self.ping_url = self.base_url + "gateway/k8sauth/v1/ping"

    def test_matching_user_is_authorized(self):
        """Asserted user matching the SA annotation passes pre-auth (200, OK)."""
        response = knox_get(
            self.ping_url,
            headers={SPIFFE_HEADER: VALID_SPIFFE_ID, USER_HEADER: "bob"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.text.strip(), "OK")

    def test_mismatching_user_is_forbidden(self):
        """Asserted user not matching the SA annotation is rejected (403)."""
        response = knox_get(
            self.ping_url,
            headers={SPIFFE_HEADER: VALID_SPIFFE_ID, USER_HEADER: "sam"},
        )
        self.assertEqual(response.status_code, 403)

    def test_missing_spiffe_header_is_forbidden(self):
        """Without the SPIFFE header there is nothing to validate (403)."""
        response = knox_get(self.ping_url, headers={USER_HEADER: "bob"})
        self.assertEqual(response.status_code, 403)

    def test_missing_user_header_is_forbidden(self):
        """Without the asserted-user header the principal is missing (403)."""
        response = knox_get(self.ping_url, headers={SPIFFE_HEADER: VALID_SPIFFE_ID})
        self.assertEqual(response.status_code, 403)

    def test_unparseable_spiffe_id_is_forbidden(self):
        """A SPIFFE id that is not in ns/sa form cannot be resolved (403)."""
        response = knox_get(
            self.ping_url,
            headers={SPIFFE_HEADER: "spiffe://cluster.local/not/a/valid/path",
                     USER_HEADER: "bob"},
        )
        self.assertEqual(response.status_code, 403)

    def test_unknown_service_account_is_forbidden(self):
        """A SPIFFE id pointing at a non-existent ServiceAccount is rejected (403)."""
        response = knox_get(
            self.ping_url,
            headers={SPIFFE_HEADER: "spiffe://cluster.local/ns/test/sa/does-not-exist",
                     USER_HEADER: "bob"},
        )
        self.assertEqual(response.status_code, 403)


if __name__ == "__main__":
    unittest.main()
