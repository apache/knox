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

"""End-to-end test for KnoxSSO open-redirect protection.

WebSSOResource must refuse to bounce an authenticated user to an originalUrl
whose authority embeds userinfo (``user@host``): such a target is never a
legitimate redirect.

Exercised against the ``knoxsso`` topology shipped in the CI image (Basic auth
in front, KNOXSSO service behind).
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get


class TestKnoxSsoRedirectProtection(unittest.TestCase):
    """KnoxSSO must reject userinfo-bearing redirect targets with 400."""

    def setUp(self):
        self.base_url = gateway_base_url()
        self.websso_url = self.base_url + "gateway/knoxsso/api/v1/websso"
        self.guest_auth = HTTPBasicAuth("guest", "guest-password")

    def test_websso_rejects_userinfo_in_original_url(self):
        """originalUrl with embedded userinfo must yield 400, not a redirect.

        ``%252f`` is decoded once at the servlet layer to ``%2f``, so the service
        sees ``https://localhost:8443%2f@malicious.link/`` — host ``malicious.link``
        with ``localhost:8443/`` as userinfo. KnoxSSO must refuse it.
        """
        url = (
            self.websso_url
            + "?originalUrl=https://localhost:8443%252f@malicious.link/"
        )

        response = knox_get(url, auth=self.guest_auth, allow_redirects=False)

        self.assertEqual(
            response.status_code,
            400,
            msg=f"Expected 400 for userinfo redirect target, got "
            f"{response.status_code}: {response.text}",
        )


if __name__ == "__main__":
    unittest.main()
