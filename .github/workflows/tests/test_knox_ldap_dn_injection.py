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

"""Integration tests for LDAP DN-injection hardening in KnoxLdapRealm.

Runs against the live knox + ldap docker-compose stack via the knoxldap
topology, whose ShiroProvider binds with:
    main.ldapRealm.userDnTemplate = uid={0},ou=people,dc=proxy,dc=org

Two guarantees:
  * regression - legitimate demo-LDAP users still authenticate (HTTP 200);
  * injection  - usernames carrying DN metacharacters do not authenticate
                 (HTTP 401), because the username is RFC 4514-escaped before
                 it is substituted into the bind DN, so it cannot alter the
                 DN structure and resolves to no real directory entry.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get

# Usernames whose DN metacharacters (',', '=', '*', '(', ')') would rewrite or
# widen the bind DN uid={0},ou=people,dc=proxy,dc=org if left unescaped. After
# RFC 4514 escaping each is a single literal uid value matching no entry, so the
# bind fails and Knox returns 401. None of these must ever authenticate.
INJECTION_USERNAMES = [
    "guest,ou=people,dc=proxy,dc=org",
    "admin,ou=people,dc=proxy,dc=org",
    "guest,ou=admin",
    "*",
    "guest)(uid=*",
    "uid=admin,ou=people,dc=proxy,dc=org",
]


class TestKnoxLdapDnInjection(unittest.TestCase):
    """Valid LDAP auth still works; DN-injection usernames are rejected."""

    def setUp(self):
        self.base_url = gateway_base_url()
        # Topology name derives from the filename knoxldap.xml.
        self.topology_url = self.base_url + "gateway/knoxldap/auth/api/v1/pre"

    def test_valid_guest_authenticates(self):
        """Regression: a legitimate user still binds and authenticates (200)."""
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth("guest", "guest-password"),
        )
        self.assertEqual(
            response.status_code,
            200,
            f"guest should authenticate; got {response.status_code}",
        )
        self.assertEqual(response.headers.get("x-knox-actor-username"), "guest")

    def test_valid_admin_authenticates(self):
        """Regression: a second legitimate user still authenticates (200)."""
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth("admin", "admin-password"),
        )
        self.assertEqual(
            response.status_code,
            200,
            f"admin should authenticate; got {response.status_code}",
        )
        self.assertEqual(response.headers.get("x-knox-actor-username"), "admin")

    def test_dn_injection_usernames_are_rejected(self):
        """Injection: DN-metacharacter usernames must not authenticate (401)."""
        for username in INJECTION_USERNAMES:
            with self.subTest(username=username):
                response = knox_get(
                    self.topology_url,
                    auth=HTTPBasicAuth(username, "guest-password"),
                )
                self.assertEqual(
                    response.status_code,
                    401,
                    f"injection username {username!r} must be rejected; "
                    f"got {response.status_code}",
                )
                self.assertNotIn("x-knox-actor-username", response.headers)


if __name__ == "__main__":
    unittest.main()
