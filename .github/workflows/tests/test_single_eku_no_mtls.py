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

"""Integration test for single-EKU mode with mTLS turned OFF.

Started against a Knox gateway with gateway.tls.single.eku.enabled=true but
gateway.client.auth.needed=false and no outbound two-way SSL (supplied by
docker-compose.single-eku-no-mtls.yml, which also sets
KNOX_SINGLE_EKU_NO_MTLS=true). Proves single-EKU no longer forces inbound mTLS:
a request with NO client certificate must complete the TLS handshake.
"""

import os
import unittest

import requests
import urllib3

KNOX_URL = os.environ.get("KNOX_GATEWAY_URL", "https://knox:8443/").rstrip("/")
HEALTH_PATH = "/gateway/health/v1/ping"

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


@unittest.skipUnless(
    os.environ.get("KNOX_SINGLE_EKU_NO_MTLS") == "true",
    "single-EKU/no-mTLS scenario not configured (apply docker-compose.single-eku-no-mtls.yml)",
)
class SingleEkuNoMtlsTest(unittest.TestCase):
    """Asserts single-EKU startup does not force inbound client authentication."""

    def test_plain_request_without_client_cert_completes_handshake(self):
        """No client cert + client.auth off: the TLS handshake completes (not rejected).

        A completed handshake returning any HTTP status proves both that the server came
        up in single-EKU mode (serverAuth-only identity validated) and that it does not
        demand a client certificate. A ConnectionError/SSLError would mean either Knox
        never started or it is still forcing mTLS.
        """
        try:
            resp = requests.get(KNOX_URL + HEALTH_PATH, verify=False, timeout=10)
        except (requests.exceptions.SSLError,
                requests.exceptions.ConnectionError) as exc:
            self.fail(f"Knox rejected a no-client-cert request in single-EKU/no-mTLS mode: {exc}")
        self.assertIsNotNone(resp.status_code)


if __name__ == "__main__":
    unittest.main()
