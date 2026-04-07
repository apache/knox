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
import os
import unittest

import requests
import urllib3
from requests.auth import HTTPBasicAuth

# Suppress InsecureRequestWarning since we use verify=False for self-signed certs
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def _base_url() -> str:
    url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
    return url if url.endswith("/") else (url + "/")


class TestKnoxAuthServicePreAuthAndPaths(unittest.TestCase):
    def setUp(self):
        self.base_url = _base_url()
        self.preauth_url = self.base_url + "gateway/knoxldap/auth/api/v1/pre"
        self.extauthz_url = self.base_url + "gateway/knoxldap/auth/api/v1/extauthz"

    def test_preauth_requires_auth(self):
        response = requests.get(self.preauth_url, verify=False, timeout=30)
        self.assertEqual(response.status_code, 401)

    def test_preauth_bad_credentials_unauthorized(self):
        response = requests.get(
            self.preauth_url,
            auth=HTTPBasicAuth("baduser", "badpass"),
            verify=False,
            timeout=30,
        )
        self.assertEqual(response.status_code, 401)

    def test_preauth_post_supported(self):
        response = requests.post(
            self.preauth_url,
            auth=HTTPBasicAuth("guest", "guest-password"),
            verify=False,
            timeout=30,
        )
        self.assertEqual(response.status_code, 200)

        actor_id_header = "x-knox-actor-username"
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], "guest")

    def test_extauthz_additional_path_not_ignored_in_knoxldap(self):
        response = requests.get(
            self.extauthz_url + "/does-not-exist",
            auth=HTTPBasicAuth("guest", "guest-password"),
            verify=False,
            timeout=30,
        )
        self.assertEqual(response.status_code, 404)


if __name__ == "__main__":
    unittest.main()

