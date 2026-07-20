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

"""Integration tests for general LDAP search through the embedded Knox LDAP proxy.

These exercise the LdapProxyBackend.search() path (KNOX-3341): clients can query
the embedded Knox LDAP service - which proxies to the demo LDAP backend - by
objectClass, cn and uid (including wildcards), not just by a single uid lookup.

The connection is made over LDAPS: the embedded Knox LDAP service is configured
with gateway.ldap.ssl.enabled=true, and it in turn proxies to the demo LDAP
backend over LDAPS as well (gateway.ldap.interceptor.demoldap.url=ldaps://...).
The gateway presents a self-signed dev certificate in CI, so certificate
validation is disabled on the client side.
"""

from __future__ import annotations

import os
import ssl
import unittest
from urllib.parse import urlparse

import ldap3

# The embedded Knox LDAP service secure port (see gateway-site.xml: gateway.ldap.port
# with gateway.ldap.ssl.enabled=true).
KNOX_LDAP_PORT = 33390

BASE_DN = "dc=hadoop,dc=apache,dc=org"
PEOPLE_BASE = f"ou=people,{BASE_DN}"
GROUPS_BASE = f"ou=groups,{BASE_DN}"

PROXY_BASE_DN = "dc=proxy,dc=org"
PROXY_PEOPLE_BASE = f"ou=people,{PROXY_BASE_DN}"
PROXY_GROUPS_BASE = f"ou=groups,{PROXY_BASE_DN}"

# A valid backend user used to bind to the proxy before searching.
BIND_DN = f"uid=guest,{PEOPLE_BASE}"
BIND_PASSWORD = "guest-password"


def knox_host() -> str:
    """Derive the Knox host from KNOX_GATEWAY_URL (defaults to localhost)."""
    url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
    return urlparse(url).hostname or "localhost"


class TestKnoxLdapProxySearch(unittest.TestCase):
    """Verify general search requests are proxied to the demo LDAP backend."""

    def setUp(self) -> None:
        # Self-signed dev certificate in CI: connect over TLS but skip validation.
        tls = ldap3.Tls(validate=ssl.CERT_NONE)
        server = ldap3.Server(
            knox_host(), port=KNOX_LDAP_PORT, use_ssl=True, tls=tls, get_info=ldap3.NONE
        )
        self.connection = ldap3.Connection(
            server, user=BIND_DN, password=BIND_PASSWORD, auto_bind=True
        )

    def tearDown(self) -> None:
        self.connection.unbind()

    def rdn_values(self, base: str, ldap_filter: str) -> list[str]:
        """Run a subtree search and return the leading RDN value of each entry."""
        self.connection.search(base, ldap_filter, search_scope=ldap3.SUBTREE)
        values = []
        for entry in self.connection.entries:
            # entry_dn looks like "uid=guest,ou=people,..." or "cn=level1,ou=groups,..."
            first_rdn = entry.entry_dn.split(",", 1)[0]
            values.append(first_rdn.split("=", 1)[1])
        return values

    def test_search_all_users_by_objectclass(self) -> None:
        """All inetOrgPerson entries under ou=people are returned."""
        users = self.rdn_values(PEOPLE_BASE, "(objectClass=inetOrgPerson)")
        for expected in ("guest", "admin", "sam", "tom", "recursiveUser"):
            self.assertIn(expected, users)

    def test_search_all_groups_by_objectclass(self) -> None:
        """All groupOfNames entries under ou=groups are returned."""
        groups = self.rdn_values(GROUPS_BASE, "(objectClass=groupOfNames)")
        for expected in ("analyst", "scientist", "admin", "level1", "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard(self) -> None:
        """A cn wildcard filter returns only the matching groups."""
        groups = self.rdn_values(GROUPS_BASE, "(cn=level*)")
        self.assertEqual({"level1", "level2", "level3"}, set(groups))

    def test_search_user_by_uid(self) -> None:
        """A single user can still be looked up by uid."""
        users = self.rdn_values(PEOPLE_BASE, "(uid=sam)")
        self.assertIn("sam", users)

    def test_search_all_users_by_objectclass_proxy_dn(self) -> None:
        """All inetOrgPerson entries under ou=people are returned."""
        users = self.rdn_values(PROXY_PEOPLE_BASE, "(objectClass=inetOrgPerson)")
        for expected in ("guest", "admin", "sam", "tom", "recursiveUser"):
            self.assertIn(expected, users)

    def test_search_all_groups_by_objectclass_proxy_dn(self) -> None:
        """All groupOfNames entries under ou=groups are returned."""
        groups = self.rdn_values(PROXY_GROUPS_BASE, "(objectClass=groupOfNames)")
        for expected in ("analyst", "scientist", "admin", "level1", "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard_proxy_dn(self) -> None:
        """A cn wildcard filter returns only the matching groups."""
        groups = self.rdn_values(PROXY_GROUPS_BASE, "(cn=level*)")
        self.assertEqual({"level1", "level2", "level3"}, set(groups))

    def test_search_user_by_uid_proxy_dn(self) -> None:
        """A single user can still be looked up by uid."""
        users = self.rdn_values(PROXY_PEOPLE_BASE, "(uid=sam)")
        self.assertIn("sam", users)


if __name__ == "__main__":
    unittest.main()
