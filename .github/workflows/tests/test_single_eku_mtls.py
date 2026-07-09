# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Integration tests for Knox single-EKU mode and inbound mTLS enforcement.

These tests only make sense against a Knox gateway started in single-EKU mode
(gateway.tls.single.eku.enabled=true + gateway.client.auth.needed=true). That
configuration is supplied by the docker-compose.single-eku.yml OVERRIDE, which
also sets KNOX_SINGLE_EKU=true on the tests container.

The default compose stack does NOT enable single-EKU mode (its other tests make
requests without a client cert), so this whole class is skipped unless
KNOX_SINGLE_EKU=true. To run it (from repo root):

  docker compose \
    -f .github/workflows/compose/docker-compose.yml \
    -f .github/workflows/compose/docker-compose.single-eku.yml \
    run --rm tests python -m unittest test_single_eku_mtls -v
"""

import os
import unittest

import requests
import urllib3

# Knox base URL inside the compose network. The tests service receives
# KNOX_GATEWAY_URL=https://knox:8443/ (see docker-compose.yml).
KNOX_URL = os.environ.get("KNOX_GATEWAY_URL", "https://knox:8443/").rstrip("/")
HEALTH_PATH = "/gateway/health/v1/ping"

# PEM client material mounted into the tests container by the single-EKU
# compose override (its cert carries only the clientAuth EKU).
CLIENT_CERT = os.environ.get("KNOX_CLIENT_CERT")  # PEM cert
CLIENT_KEY = os.environ.get("KNOX_CLIENT_KEY")    # PEM key

# Self-signed dev TLS in CI; silence the noisy InsecureRequestWarning.
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


@unittest.skipUnless(
    os.environ.get("KNOX_SINGLE_EKU") == "true",
    "single-EKU mode not configured (apply docker-compose.single-eku.yml)",
)
class SingleEkuMtlsTest(unittest.TestCase):
    """Asserts single-EKU startup and inbound client-auth enforcement."""

    def test_server_is_up_in_single_eku_mode(self):
        """Knox listening in single-EKU mode proves fail-fast validation passed.

        With gateway.client.auth.needed=true an UNauthenticated request must NOT
        complete the handshake -- the server demands a client cert -- so "server
        up" cannot be probed with a plain request (that is the no-cert rejection
        case below). Instead:

          * if a client cert is available, a completed mTLS handshake proves the
            server is up AND single-EKU fail-fast validation passed;
          * otherwise, a "certificate required" TLS alert (rather than a refused
            connection) is itself proof the server is listening and enforcing
            client auth -- whereas a ConnectionError means Knox never came up
            (e.g. fail-fast validation aborted startup).
        """
        if CLIENT_CERT and CLIENT_KEY:
            try:
                resp = requests.get(
                    KNOX_URL + "/gateway/",
                    cert=(CLIENT_CERT, CLIENT_KEY),
                    verify=False,
                    timeout=10,
                )
            except (requests.exceptions.SSLError,
                    requests.exceptions.ConnectionError) as exc:
                self.fail(f"Knox not reachable via mTLS in single-EKU mode: {exc}")
            self.assertIsNotNone(resp.status_code)
        else:
            # No client material: a TLS-layer rejection proves the server is up
            # and enforcing client auth; a ConnectionError means it never started.
            try:
                requests.get(KNOX_URL + "/gateway/", verify=False, timeout=10)
            except requests.exceptions.SSLError:
                pass  # server up + demanding a client cert -> proof of life
            except requests.exceptions.ConnectionError as exc:
                self.fail(f"Knox is not reachable in single-EKU mode: {exc}")

    def test_inbound_request_without_client_cert_is_rejected(self):
        """gateway.client.auth.needed=true rejects a request with no client cert.

        Depending on the Jetty/JSSE TLS behavior this surfaces either as an
        SSLError (handshake failure) or a ConnectionError (peer reset), so accept
        either.
        """
        with self.assertRaises(
            (requests.exceptions.SSLError, requests.exceptions.ConnectionError)
        ):
            requests.get(KNOX_URL + HEALTH_PATH, verify=False, timeout=10)

    @unittest.skipUnless(
        CLIENT_CERT and CLIENT_KEY,
        "client cert/key not provided; mTLS happy-path skipped",
    )
    def test_inbound_request_with_client_cert_completes_handshake(self):
        """A trusted client cert lets the mTLS handshake complete.

        Only the handshake is asserted (not the HTTP status), since this proves
        inbound client authentication negotiates successfully.
        """
        resp = requests.get(
            KNOX_URL + HEALTH_PATH,
            cert=(CLIENT_CERT, CLIENT_KEY),
            verify=False,
            timeout=10,
        )
        self.assertIsNotNone(resp.status_code)


if __name__ == "__main__":
    unittest.main()
