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

"""End-to-end tests for the per-issuer CA bundle (jwksCa) of trusted OIDC issuers.

The throwaway k3s cluster is an OIDC issuer at https://k3s:6443 whose discovery and
JWKS endpoints are served with the cluster's self-signed CA (see docker-compose.yml).
A trusted issuer registered with a jwksCa fetches those endpoints trusting only that
bundle, given either inline as PEM or as a Kubernetes Secret reference
(secret:[<namespace>/]<name>#<key>, namespace defaulting to "knox"). Knox reads such
Secrets through the same admin kubeconfig its ServiceAccountValidator uses.

The k3s CA is deliberately not in the JVM cacerts, so the same issuer registered
without a jwksCa is rejected: the bundle is the only trust Knox has for k3s, and
registering it for one issuer does not leak it into the global truststore. Registering
the issuer with an unrelated CA and seeing the exchange rejected proves that only the
configured bundle is trusted.

Every CA change below takes effect without restarting Knox: re-registering an inline
bundle, editing the referenced Secret followed by refresh-jwks, and editing the Secret
alone, which Knox picks up when the old bundle fails the TLS handshake.
"""

import os
import re
import shutil
import ssl
import tempfile
import time
import unittest
from base64 import b64decode
from urllib.parse import urlparse

import requests
from requests.auth import HTTPBasicAuth

from common_utils import KNOX_REQUEST_TIMEOUT, gateway_base_url, knox_delete, knox_get, knox_post
from delegation_helpers import token_exchange

# The k3s OIDC issuer, and the files the k8s-bootstrap service writes to the shared volume:
# its serving CA, the admin kubeconfig Knox also uses, and a projected SA token it issued.
ISSUER_URL = "https://k3s:6443"
K3S_CA_FILE = "/k3s/k3s-ca.crt"
KUBECONFIG_FILE = "/k3s/knox-kubeconfig.yaml"
SA_TOKEN_FILE = "/k3s/sa-token"

# The only principal the knoxidf-admin ACL permits.
ADMIN_USER = "admin"
ADMIN_PASSWORD = "admin-password"

# Knox's default namespace for a secret reference without one (see knox-namespace.yaml).
DEFAULT_SECRET_NAMESPACE = "knox"
# Any other existing namespace, for a reference that names its namespace explicitly.
OTHER_SECRET_NAMESPACE = "test"
SECRET_NAME = "k3s-issuer-ca"
SECRET_KEY = "ca.crt"

# Mirrors JwksCa.MAX_LENGTH, the limit on an inline jwksCa.
JWKS_CA_MAX_LENGTH = 65000

# Knox re-reads a Secret after a TLS trust failure at most this often per issuer
# (IssuerTrustProvider.MIN_RELOAD_INTERVAL), plus a margin.
FAILURE_RELOAD_WAIT_SECS = 35


def _kubeconfig_value(kubeconfig, field):
    """Return the first value of a scalar field of a kubeconfig (no YAML parser needed)."""
    match = re.search(r"^\s*" + re.escape(field) + r":\s*(\S+)\s*$", kubeconfig, re.MULTILINE)
    if match is None:
        raise ValueError(f"{field} not found in {KUBECONFIG_FILE}")
    return match.group(1)


class _KubernetesSecrets:
    """Minimal client for the k3s Secrets API, using the bootstrap's admin client certificate."""

    def __init__(self):
        with open(KUBECONFIG_FILE, encoding="utf-8") as handle:
            kubeconfig = handle.read()
        self._server = _kubeconfig_value(kubeconfig, "server")
        self._workdir = tempfile.mkdtemp(prefix="k3s-client-")
        cert_file = os.path.join(self._workdir, "client.crt")
        key_file = os.path.join(self._workdir, "client.key")
        with open(cert_file, "wb") as handle:
            handle.write(b64decode(_kubeconfig_value(kubeconfig, "client-certificate-data")))
        with open(key_file, "wb") as handle:
            handle.write(b64decode(_kubeconfig_value(kubeconfig, "client-key-data")))
        self._request_options = {
            "cert": (cert_file, key_file),
            "verify": K3S_CA_FILE,
            "timeout": KNOX_REQUEST_TIMEOUT,
        }

    def close(self):
        """Remove the extracted client credentials."""
        shutil.rmtree(self._workdir, ignore_errors=True)

    def _url(self, namespace, name=None):
        url = f"{self._server}/api/v1/namespaces/{namespace}/secrets"
        return url if name is None else f"{url}/{name}"

    def put(self, namespace, name, key, value):
        """Create the Secret, or replace its data if it already exists."""
        body = {
            "apiVersion": "v1",
            "kind": "Secret",
            "metadata": {"name": name, "namespace": namespace},
            "stringData": {key: value},
        }
        response = requests.put(self._url(namespace, name), json=body, **self._request_options)
        if response.status_code == 404:
            response = requests.post(self._url(namespace), json=body, **self._request_options)
        if response.status_code not in (200, 201):
            raise AssertionError(f"writing Secret {namespace}/{name} failed: "
                                 f"{response.status_code} {response.text}")

    def delete(self, namespace, name):
        """Delete the Secret if it exists."""
        requests.delete(self._url(namespace, name), **self._request_options)


class TestK8sJwksCa(unittest.TestCase):
    """Token exchange of real k8s ServiceAccount tokens against an issuer with a jwksCa."""

    def setUp(self):
        base_url = gateway_base_url()
        self.admin_url = base_url + "gateway/knoxidf-admin/knoxidf/admin/v1/trusted-oidc-issuers"
        self.token_url = base_url + "gateway/knoxidf-token/knoxidf/api/v1/token"
        try:
            with open(SA_TOKEN_FILE, encoding="utf-8") as handle:
                self.sa_token = handle.read().strip()
            with open(K3S_CA_FILE, encoding="utf-8") as handle:
                self.k3s_ca = handle.read()
            self.secrets = _KubernetesSecrets()
        except OSError as e:
            self.skipTest(f"k3s fixtures not available: {e}")
        self.addCleanup(self.secrets.close)
        # A genuine certificate that did not issue the k3s serving certificate: Knox's own.
        gateway = urlparse(base_url)
        self.unrelated_ca = ssl.get_server_certificate(
            (gateway.hostname, gateway.port or 443), timeout=KNOX_REQUEST_TIMEOUT)
        self._remove_issuer()
        self.addCleanup(self._remove_issuer)

    def _admin_auth(self):
        """HTTP Basic credentials for the knoxidf-admin API."""
        return HTTPBasicAuth(ADMIN_USER, ADMIN_PASSWORD)

    def _register_issuer(self, jwks_ca, dynamic_jwks=True):
        """Register the k3s issuer with the given jwksCa."""
        return knox_post(
            self.admin_url,
            json={"issuerUrl": ISSUER_URL, "dynamicJwks": dynamic_jwks, "clusterName": "k3s",
                  "jwksCa": jwks_ca},
            auth=self._admin_auth(),
        )

    def _register_issuer_expecting_created(self, jwks_ca):
        response = self._register_issuer(jwks_ca)
        self.assertEqual(response.status_code, 201, response.text)

    def _remove_issuer(self):
        """Deregister the k3s issuer (idempotent)."""
        knox_delete(self.admin_url, params={"issuerUrl": ISSUER_URL}, auth=self._admin_auth())

    def _refresh_jwks(self):
        """Re-resolve the issuer's JWKS URI, which also re-reads its jwksCa."""
        response = knox_post(self.admin_url + "/refresh-jwks", params={"issuerUrl": ISSUER_URL},
                             auth=self._admin_auth())
        self.assertEqual(response.status_code, 204, response.text)

    def _registered_issuer(self):
        """Return the registry entry of the k3s issuer, or None."""
        response = knox_get(self.admin_url, auth=self._admin_auth())
        self.assertEqual(response.status_code, 200, response.text)
        for entry in response.json():
            if entry.get("issuerUrl") == ISSUER_URL:
                return entry
        return None

    def _put_secret(self, namespace, value):
        self.secrets.put(namespace, SECRET_NAME, SECRET_KEY, value)
        self.addCleanup(self.secrets.delete, namespace, SECRET_NAME)

    def _assert_exchange_succeeds(self):
        response = token_exchange(self.token_url, self.sa_token)
        self.assertEqual(response.status_code, 200, response.text)
        self.assertIn("access_token", response.json())

    def _assert_exchange_rejected(self):
        response = token_exchange(self.token_url, self.sa_token)
        self.assertEqual(response.status_code, 401, response.text)
        self.assertNotIn("access_token", response.text)

    def test_invalid_jwks_ca_is_rejected_at_registration(self):
        """A jwksCa that can never be used is refused with 400 and nothing is registered."""
        cases = {
            "static JWKS": (self.k3s_ca, False),
            "oversized bundle": ("x" * (JWKS_CA_MAX_LENGTH + 1), True),
            "secret reference without key": (f"secret:{SECRET_NAME}", True),
            "secret name that is not DNS-1123": ("secret:knox/K3S_CA#ca.crt", True),
            "not a PEM bundle": ("not a certificate", True),
            "corrupt PEM": ("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n", True),
        }
        for case, (jwks_ca, dynamic_jwks) in cases.items():
            with self.subTest(case):
                response = self._register_issuer(jwks_ca, dynamic_jwks)
                self.assertEqual(response.status_code, 400, response.text)
                self.assertIn("invalid_request", response.text)
                self.assertIn("jwksCa", response.text)
                self.assertIsNone(self._registered_issuer())

    def test_inline_ca_validates_issuer(self):
        """An inline k3s CA lets Knox fetch discovery + JWKS and accept the SA token."""
        self._register_issuer_expecting_created(self.k3s_ca)
        self.assertEqual(self._registered_issuer().get("jwksCa"), self.k3s_ca.strip())
        self._assert_exchange_succeeds()

    def test_inline_ca_is_pinned(self):
        """With an unrelated jwksCa the exchange fails: only the configured bundle is trusted."""
        self._register_issuer_expecting_created(self.unrelated_ca)
        self._assert_exchange_rejected()

    def test_ca_does_not_leak_into_default_truststore(self):
        """Once the k3s CA has been trusted via jwksCa, the issuer without one is still rejected."""
        self._register_issuer_expecting_created(self.k3s_ca)
        self._assert_exchange_succeeds()

        self._remove_issuer()
        self._register_issuer_expecting_created(None)
        self.assertIsNone(self._registered_issuer().get("jwksCa"))
        self._assert_exchange_rejected()

    def test_reregistered_inline_ca_applies_without_restart(self):
        """Re-registering the issuer (delete + create) with a new inline bundle applies at once."""
        self._register_issuer_expecting_created(self.unrelated_ca)
        self._assert_exchange_rejected()

        self._remove_issuer()
        self._register_issuer_expecting_created(self.k3s_ca)
        self._assert_exchange_succeeds()

        self._remove_issuer()
        self._register_issuer_expecting_created(self.unrelated_ca)
        self._assert_exchange_rejected()

    def test_secret_ca_in_default_namespace_validates_issuer(self):
        """secret:<name>#<key> is read from the "knox" namespace."""
        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.k3s_ca)
        jwks_ca = f"secret:{SECRET_NAME}#{SECRET_KEY}"
        self._register_issuer_expecting_created(jwks_ca)
        self.assertEqual(self._registered_issuer().get("jwksCa"), jwks_ca)
        self._assert_exchange_succeeds()

    def test_secret_ca_in_named_namespace_validates_issuer(self):
        """secret:<namespace>/<name>#<key> is read from the named namespace."""
        self._put_secret(OTHER_SECRET_NAMESPACE, self.k3s_ca)
        self._register_issuer_expecting_created(
            f"secret:{OTHER_SECRET_NAMESPACE}/{SECRET_NAME}#{SECRET_KEY}")
        self._assert_exchange_succeeds()

    def test_missing_secret_fails_closed_until_created(self):
        """A reference to a missing Secret registers, rejects tokens, and works once created."""
        self.secrets.delete(DEFAULT_SECRET_NAMESPACE, SECRET_NAME)
        self._register_issuer_expecting_created(f"secret:{SECRET_NAME}#{SECRET_KEY}")
        self._assert_exchange_rejected()

        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.k3s_ca)
        self._refresh_jwks()
        self._assert_exchange_succeeds()

    def test_missing_secret_key_fails_closed(self):
        """A reference to a key the Secret does not hold rejects tokens."""
        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.k3s_ca)
        self._register_issuer_expecting_created(f"secret:{SECRET_NAME}#other.crt")
        self._assert_exchange_rejected()

    def test_rotated_secret_ca_applies_on_refresh_jwks(self):
        """Editing the Secret and calling refresh-jwks applies the new bundle without a restart."""
        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.unrelated_ca)
        self._register_issuer_expecting_created(f"secret:{SECRET_NAME}#{SECRET_KEY}")
        self._assert_exchange_rejected()

        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.k3s_ca)
        self._refresh_jwks()
        self._assert_exchange_succeeds()

    def test_rotated_secret_ca_applies_after_tls_failure(self):
        """Editing the Secret alone is enough: a failing handshake makes Knox re-read it.

        Knox re-reads a Secret after a TLS trust failure at most once per 30s per issuer, and
        it read the old bundle on the rejected exchange, so the test waits that interval out.
        """
        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.unrelated_ca)
        self._register_issuer_expecting_created(f"secret:{SECRET_NAME}#{SECRET_KEY}")
        self._assert_exchange_rejected()

        self._put_secret(DEFAULT_SECRET_NAMESPACE, self.k3s_ca)
        time.sleep(FAILURE_RELOAD_WAIT_SECS)
        self._assert_exchange_succeeds()


if __name__ == "__main__":
    unittest.main()
