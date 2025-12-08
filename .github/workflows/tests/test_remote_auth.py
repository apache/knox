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

########################################################
# This test is verifying the behavior of the RemoteAuthProvider.
# It is using the 'auth/api/v1/pre' endpoint to get the actor ID and group headers.
# It is using the 'guest' user to get the guest user headers.
# It is using the 'admin' user to get the admin user headers.
# It is verifying that the actor ID and group headers are correct.
# It is verifying that the actor ID and group headers are not empty.
# It is verifying that the actor ID and group headers are not None.
########################################################
# Suppress InsecureRequestWarning
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class TestRemoteAuth(unittest.TestCase):
    def setUp(self):
        self.base_url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
        if not self.base_url.endswith("/"):
            self.base_url += "/"
        self.topology_url = self.base_url + "gateway/remoteauth/auth/api/v1/pre"

    def test_remote_auth_success(self):
        """
        Verify that valid credentials result in successful authentication
        and correct identity assertion using knoxldap as remote auth.
        """
        print(f"\nTesting remote auth success against {self.topology_url}")
        # The RemoteAuthFilter forwards Authorization header to knoxldap.
        # knoxldap accepts guest:guest-password
        response = requests.get(
            self.topology_url,
            auth=HTTPBasicAuth('guest', 'guest-password'),
            verify=False,
            timeout=30
        )
        print(f"Status Code: {response.status_code}")
        print(f"Headers: {response.headers}")
        self.assertEqual(response.status_code, 200)

        # RemoteAuthFilter sets principal from x-knox-actor-username (guest)
        # KNOX-AUTH-SERVICE (pre endpoint) echoes principal in X-Knox-Actor-ID (default)
        self.assertIn('X-Knox-Actor-ID', response.headers)
        self.assertEqual(response.headers['X-Knox-Actor-ID'], 'guest')

    def test_remote_auth_admin_groups(self):
        """
        Verify admin user gets multiple groups from knoxldap mapping
        """
        print(f"\nTesting remote auth admin against {self.topology_url}")
        response = requests.get(
            self.topology_url,
            auth=HTTPBasicAuth('admin', 'admin-password'),
            verify=False,
            timeout=30
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers['X-Knox-Actor-ID'], 'admin')

        # knoxldap maps admin to: longGroupName1,longGroupName2,longGroupName3,longGroupName4
        # RemoteAuthFilter picks these up from x-knox-actor-groups-*
        # And KNOX-AUTH-SERVICE echoes them back in X-Knox-Actor-Groups-*
        
        group_headers = [h for h in response.headers if h.lower().startswith('x-knox-actor-groups')]
        all_groups = []
        for h in group_headers:
            all_groups.extend(response.headers[h].split(','))
        
        print(f"Found groups: {all_groups}")
        self.assertIn('longGroupName1', all_groups)
        self.assertIn('longGroupName2', all_groups)

    def test_remote_auth_failure(self):
        """
        Verify invalid credentials result in 401
        """
        print(f"\nTesting remote auth failure against {self.topology_url}")
        response = requests.get(
            self.topology_url,
            auth=HTTPBasicAuth('baduser', 'badpass'),
            verify=False,
            timeout=30
        )
        print(f"Status Code: {response.status_code}")
        # When remote auth fails (knoxldap returns 401), RemoteAuthFilter should return 401
        self.assertEqual(response.status_code, 401)

if __name__ == '__main__':
    unittest.main()
