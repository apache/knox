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

"""Integration tests for general LDAP search through the embedded Knox LDAP proxy,
with role lookup configured.

These reuse TestKnoxLdapProxySearch's connection setup and the tests that are
unaffected by role lookup (they run again here against the role-lookup-configured
gateway), and override the group-lookup tests: group RDNs are expected to come back
mapped to platform:-prefixed roles, unless the search bypasses role lookup via
ROLE_LOOKUP_BYPASS_CONTROL.
"""

from __future__ import annotations

import unittest

from test_knox_ldap_proxy_search import (
    GROUPS_BASE,
    PROXY_GROUPS_BASE,
    TestKnoxLdapProxySearch,
)

ROLE_LOOKUP_BYPASS_CONTROL = ("1.3.6.1.4.1.18060.18.0.1", False, bytearray([0x01, 0x01, 0xff]))


class TestKnoxLdapProxySearchWithRoleLookup(TestKnoxLdapProxySearch):
    """Verify general search requests are proxied to the demo LDAP backend
    with role lookup configured."""

    def test_search_all_groups_by_objectclass(self) -> None:
        """All groupOfNames entries under ou=groups come back mapped to roles."""
        groups = self.rdn_values(self.search(GROUPS_BASE, "(objectClass=groupOfNames)"))
        for expected in ("platform:analyst", "platform:scientist",
                         "platform:admin", "platform:level1",
                         "platform:level2", "platform:level3"):
            self.assertIn(expected, groups)

    def test_search_all_groups_by_objectclass_bypass_role_lookup(self) -> None:
        """Role lookup can be bypassed, returning the unmapped group names."""
        groups = self.rdn_values(
            self.search(GROUPS_BASE, "(objectClass=groupOfNames)", [ROLE_LOOKUP_BYPASS_CONTROL]))
        for expected in ("analyst", "scientist",
                         "admin", "level1",
                         "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard(self) -> None:
        """A cn wildcard filter returns only the matching groups, mapped to roles."""
        groups = self.rdn_values(self.search(GROUPS_BASE, "(cn=level*)"))
        self.assertEqual({"platform:level1", "platform:level2", "platform:level3"}, set(groups))

    def test_search_all_groups_by_objectclass_proxy_dn(self) -> None:
        """All groupOfNames entries under ou=groups come back mapped to roles."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(objectClass=groupOfNames)"))
        for expected in ("platform:analyst", "platform:scientist",
                         "platform:admin", "platform:level1",
                         "platform:level2", "platform:level3"):
            self.assertIn(expected, groups)

    def test_search_all_groups_by_objectclass_proxy_dn_bypass_role_lookup(self) -> None:
        """Role lookup can be bypassed, returning the unmapped group names."""
        groups = self.rdn_values(
            self.search(PROXY_GROUPS_BASE,
                        "(objectClass=groupOfNames)",
                        [ROLE_LOOKUP_BYPASS_CONTROL]))
        for expected in ("analyst", "scientist",
                         "admin", "level1",
                         "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard_proxy_dn(self) -> None:
        """A cn wildcard filter returns only the matching groups, mapped to roles."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(cn=level*)"))
        self.assertEqual({"platform:level1", "platform:level2", "platform:level3"}, set(groups))

    def test_search_groups_by_cn_wildcard_proxy_dn_bypass_role_lookup(self) -> None:
        """Role lookup can be bypassed, returning the unmapped group names."""
        groups = self.rdn_values(
            self.search(PROXY_GROUPS_BASE, "(cn=level*)", [ROLE_LOOKUP_BYPASS_CONTROL]))
        self.assertEqual({"level1", "level2", "level3"}, set(groups))


# Drop the bare base-class reference so pytest's unittest collection doesn't also run
# it here as its own top-level test case; the subclass above still inherits its tests.
del TestKnoxLdapProxySearch


if __name__ == "__main__":
    unittest.main()
