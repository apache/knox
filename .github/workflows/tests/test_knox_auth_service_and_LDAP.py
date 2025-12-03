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
# This test is verifying the behavior of the Knox Auth Service + LDAP authentication.
# It is using the 'auth/api/v1/pre' endpoint to get the actor ID and group headers.
# It is using the 'guest' user to get the guest user headers.
# It is using the 'admin' user to get the admin user headers.
# It is verifying that the actor ID and group headers are correct.
# It is verifying that the actor ID and group headers are not empty.
# It is verifying that the actor ID and group headers are not None.
########################################################

# Suppress InsecureRequestWarning
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class TestKnoxAuthService(unittest.TestCase):
    def setUp(self):
        self.base_url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
        if not self.base_url.endswith("/"):
            self.base_url += "/"
        # The topology name is likely 'knoxldap' based on the filename knoxldap.xml
        self.topology_url = self.base_url + "gateway/knoxldap/auth/api/v1/pre"

    def test_auth_service_guest(self):
        """
        Verify that guest user gets the correct actor ID header.
        """
        print(f"\nTesting guest authentication against {self.topology_url}")
        response = requests.get(
            self.topology_url, 
            auth=HTTPBasicAuth('guest', 'guest-password'),
            verify=False, 
            timeout=30
        )
        
        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 200)
        
        # Check for Actor ID header
        # The config in knoxtoken.xml sets 'preauth.auth.header.actor.id.name' to 'x-knox-actor-username'
        actor_id_header = 'x-knox-actor-username'
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], 'guest')
        print(f"Verified {actor_id_header}: {response.headers[actor_id_header]}")

    def test_auth_service_admin_groups(self):
        """
        Verify that admin user gets actor ID and group headers.
        """
        print(f"\nTesting admin authentication against {self.topology_url}")
        response = requests.get(
            self.topology_url, 
            auth=HTTPBasicAuth('admin', 'admin-password'),
            verify=False, 
            timeout=30
        )
        
        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 200)
        
        # Check for Actor ID header
        actor_id_header = 'x-knox-actor-username'
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], 'admin')
        print(f"Verified {actor_id_header}: {response.headers[actor_id_header]}")
        
        # Check for Group headers
        # Config: 'preauth.auth.header.actor.groups.prefix' = 'x-knox-actor-groups'
        # We mapped admin to 'longGroupName1,longGroupName2,longGroupName3,longGroupName4'
        
        # We just verify that at least one header starting with the prefix exists
        prefix = 'x-knox-actor-groups'
        group_headers = [h for h in response.headers.keys() if h.lower().startswith(prefix.lower())]
        
        self.assertTrue(len(group_headers) > 0, f"No headers found starting with {prefix}")
        
        # Verify content of groups
        all_groups = []
        for h in group_headers:
            all_groups.extend(response.headers[h].split(','))
            print(f"Found group header {h}: {response.headers[h]}")
            
        expected_groups = ['longGroupName1', 'longGroupName2', 'longGroupName3', 'longGroupName4']
        for group in expected_groups:
            self.assertIn(group, all_groups)

if __name__ == '__main__':
    unittest.main()

