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

"""Integration tests for LDAP injection hardening in KnoxLdapRealm.

Two sinks take a client-supplied username, two topologies, one guarantee: the
username must be escaped before it reaches LDAP so it cannot alter query
structure.

  * DN injection - the ``userDnTemplate`` bind DN
    (``uid={0},ou=people,dc=proxy,dc=org``) in the ``knoxldap`` topology.
  * search-filter injection - the ``userSearchFilter``
    ``(&(objectclass=person)(uid={0}))`` of the search-then-bind
    ``ldapusersearchfilter`` topology.

For each: legitimate demo-LDAP users still authenticate (HTTP 200); usernames
carrying LDAP metacharacters do not (HTTP 401), because they are RFC 4514 (DN)
or RFC 4515 (filter) escaped and so resolve to a single literal value that
matches no directory entry instead of rewriting the query.
"""

import unittest

from requests.auth import HTTPBasicAuth

from common_utils import gateway_base_url, knox_get

DN_INJECTION_USERNAMES = [
    "guest,ou=people,dc=proxy,dc=org",
    "admin,ou=people,dc=proxy,dc=org",
    "guest,ou=admin",
    "*",
    "guest)(uid=*",
    "uid=admin,ou=people,dc=proxy,dc=org",
]

FILTER_INJECTION_USERNAMES = [
    "*",
    "*)(uid=admin",
    "*)(uid=guest",
    "admin)(|(uid=*",
    "*)(objectclass=*",
]


def assert_authenticates(testcase, url, username, password, expected_actor=None):
    """A legitimate user is found/bound and authenticates (HTTP 200)."""
    response = knox_get(url, auth=HTTPBasicAuth(username, password))
    testcase.assertEqual(
        response.status_code,
        200,
        f"{username} should authenticate; got {response.status_code}",
    )
    if expected_actor is not None:
        testcase.assertEqual(
            response.headers.get("x-knox-actor-username"), expected_actor
        )


def assert_injection_rejected(testcase, url, usernames):
    """Every metacharacter-bearing username must fail authentication (HTTP 401)."""
    for username in usernames:
        with testcase.subTest(username=username):
            response = knox_get(url, auth=HTTPBasicAuth(username, "guest-password"))
            testcase.assertEqual(
                response.status_code,
                401,
                f"injection username {username!r} must be rejected; "
                f"got {response.status_code}",
            )
            testcase.assertNotIn("x-knox-actor-username", response.headers)


class TestKnoxLdapDnInjection(unittest.TestCase):
    """userDnTemplate bind DN: valid logins work; DN-injection usernames rejected."""

    def setUp(self):
        self.topology_url = gateway_base_url() + "gateway/knoxldap/auth/api/v1/pre"

    def test_valid_guest_authenticates(self):
        """Regression: a legitimate user still binds and authenticates (200)."""
        assert_authenticates(self, self.topology_url, "guest", "guest-password", "guest")

    def test_valid_admin_authenticates(self):
        """Regression: a second legitimate user still authenticates (200)."""
        assert_authenticates(self, self.topology_url, "admin", "admin-password", "admin")

    def test_dn_injection_usernames_are_rejected(self):
        """Injection: DN-metacharacter usernames must not authenticate (401)."""
        assert_injection_rejected(self, self.topology_url, DN_INJECTION_USERNAMES)


class TestKnoxLdapFilterInjection(unittest.TestCase):
    """search-then-bind userSearchFilter (KNOX-3417): valid logins work; injection rejected."""

    def setUp(self):
        self.topology_url = gateway_base_url() + "gateway/ldapusersearchfilter/v1/ping"

    def test_valid_guest_authenticates(self):
        """Regression: a legitimate user is found by the filter and binds (200)."""
        assert_authenticates(self, self.topology_url, "guest", "guest-password")

    def test_valid_admin_authenticates(self):
        """Regression: a second legitimate user still authenticates (200)."""
        assert_authenticates(self, self.topology_url, "admin", "admin-password")

    def test_filter_injection_targeting_admin_is_rejected(self):
        """The headline attack: '*)(uid=admin' + admin's password must 401.

        Unescaped, this rewrites the filter to also match admin and lets the
        attacker bind as admin. Escaped, uid literally equals '*)(uid=admin',
        which matches no entry, so authentication fails.
        """
        response = knox_get(
            self.topology_url,
            auth=HTTPBasicAuth("*)(uid=admin", "admin-password"),
        )
        self.assertEqual(
            response.status_code,
            401,
            f"filter-injection username must be rejected; got {response.status_code}",
        )
        self.assertNotEqual(response.headers.get("x-knox-actor-username"), "admin")

    def test_filter_injection_usernames_are_rejected(self):
        """Injection: filter-metacharacter usernames must not authenticate (401)."""
        assert_injection_rejected(self, self.topology_url, FILTER_INJECTION_USERNAMES)


if __name__ == "__main__":
    unittest.main()
