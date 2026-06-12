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

"""Integration tests for Knox Auth Service with LDAP authentication."""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import collect_actor_group_values, gateway_base_url, knox_get


class TestKnoxAuthService(unittest.TestCase):
    """Verify actor ID and group headers from the knoxldap preauth endpoint."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # The topology name is based on the filename knoxldap.xml
        self.topology_url = self.base_url + "gateway/knoxldap/auth/api/v1/pre"

    def test_auth_service_guest(self):
        """
        Verify that guest user gets the correct actor ID header.
        """
        print(f"\nTesting guest authentication against {self.topology_url}")
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth('guest', 'guest-password'),
        )

        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 200)

        # Check for Actor ID header
        # The config in knoxldap.xml sets 'preauth.auth.header.actor.id.name'
        # to 'x-knox-actor-username'
        actor_id_header = 'x-knox-actor-username'
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], 'guest')
        print(f"Verified {actor_id_header}: {response.headers[actor_id_header]}")

        # Check for Actor Group header - should be empty for guest
        prefix = 'x-knox-actor-groups'
        all_groups = collect_actor_group_values(response, prefix=prefix)
        self.assertEqual(
            len(all_groups),
            0,
            f"Guest user should not have any group headers starting with {prefix}",
        )

    def test_auth_service_admin_groups(self):
        """
        Verify that admin user gets actor ID and group headers.
        """
        print(f"\nTesting admin authentication against {self.topology_url}")
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth('admin', 'admin-password'),
        )

        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 200)

        # Check for Actor ID header
        actor_id_header = 'x-knox-actor-username'
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], 'admin')
        print(f"Verified {actor_id_header}: {response.headers[actor_id_header]}")

        # Config: 'preauth.auth.header.actor.groups.prefix' = 'x-knox-actor-groups'
        # We mapped admin to 'longGroupName1,longGroupName2,longGroupName3,longGroupName4'
        prefix = 'x-knox-actor-groups'
        all_groups = collect_actor_group_values(response, prefix=prefix)
        self.assertTrue(len(all_groups) > 0, f"No headers found starting with {prefix}")
        for header_name in response.headers:
            if header_name.lower().startswith(prefix.lower()):
                print(f"Found group header {header_name}: {response.headers[header_name]}")

        expected_groups = [
            'admin',
            'longGroupName1',
            'longGroupName2',
            'longGroupName3',
            'longGroupName4',
        ]
        for group in expected_groups:
            self.assertIn(group, all_groups)

    def test_auth_service_recursive_user_groups(self):
        """
        Verify that recursiveUser user gets actor ID and recursive group headers.
        """
        print(f"\nTesting recursiveUser authentication against {self.topology_url}")
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth('recursiveUser', 'recursiveUser-password'),
        )

        print(f"Status Code: {response.status_code}")
        self.assertEqual(response.status_code, 200)

        # Check for Actor ID header
        actor_id_header = 'x-knox-actor-username'
        self.assertIn(actor_id_header, response.headers)
        self.assertEqual(response.headers[actor_id_header], 'recursiveUser')
        print(f"Verified {actor_id_header}: {response.headers[actor_id_header]}")

        # Check for Actor Group headers
        prefix = 'x-knox-actor-groups'
        all_groups = collect_actor_group_values(response, prefix=prefix)

        expected_groups = ['level1', 'level2', 'level3']
        self.assertEqual(len(all_groups), len(expected_groups))
        for group in expected_groups:
            self.assertIn(group, all_groups)
        print(f"Verified recursive groups: {all_groups}")


if __name__ == '__main__':
    unittest.main()
