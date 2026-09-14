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

Two topologies are exercised:
  - knoxidf-admin  -- the KNOXIDF_ADMIN TrustedOIDCIssuers registry admin API,
                      restricted to the LDAP 'admin' user (HTTP Basic).
  - knoxidf-token  -- the JWTProvider token endpoint that performs the exchange.

Because the registry is a gateway-wide service, an issuer registered through the
admin topology is trusted by the token-exchange path on the token topology.

Covered acceptance criteria: real SA token is a genuine OIDC-issued JWT (AC2),
same-subject exchange succeeds (AC3), an unregistered issuer is rejected without
a JWKS fetch (AC6), an expired token is rejected before signature verification
(AC7), and the full admin lifecycle register/list/refresh/remove works (AC8/AC9).
"""

import base64
import json
import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get, knox_post, knox_delete

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


class TestK8sDelegation(unittest.TestCase):
    """RFC 8693 token exchange of real k8s ServiceAccount tokens through Knox."""

    def setUp(self):
        base_url = gateway_base_url()
        self.admin_url = base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/trusted-oidc-issuers"
        self.token_url = base_url + "gateway/knoxidf-token/knoxidf/api/v1/token"
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


if __name__ == "__main__":
    unittest.main()
