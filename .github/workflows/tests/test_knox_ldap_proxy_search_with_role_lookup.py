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

It also covers two role lookup features that only exist in this configuration:

* list searches - a search for all groups (from the proxy ou=groups base, or from the proxy
  root with a group objectClass filter) is answered with the roles in roles.json rather than
  the backend groups;
* reverse role lookup - a (memberOf=<role DN>) filter is rewritten to the users and groups
  that roles.json assigns the role to, so the search returns the role's members.

Backend data used in the expectations (demo LDAP users.ldif and role-lookup/roles.json):
analyst = {sam, tom}, scientist = {sam}, admin = {admin}, and level1 = {recursiveUser} with
level2 and level3 nesting the level below (gateway.ldap.recursive.group.resolution=true).
Every group maps to the platform:<group> role; user and group "admin" are both assigned
platform:admin.
"""

from __future__ import annotations

import unittest

from test_knox_ldap_proxy_search import (
    GROUPS_BASE,
    PEOPLE_BASE,
    PROXY_BASE_DN,
    PROXY_GROUPS_BASE,
    PROXY_PEOPLE_BASE,
    TestKnoxLdapProxySearch,
)

ROLE_LOOKUP_BYPASS_CONTROL = ("1.3.6.1.4.1.18060.18.0.1", False, bytearray([0x01, 0x01, 0xff]))

ALL_ROLES = {
    "platform:analyst",
    "platform:scientist",
    "platform:admin",
    "platform:level1",
    "platform:level2",
    "platform:level3",
}

# (people base, group base) pairs: the proxy DNs and the backend DNs are both accepted.
BASE_PAIRS = (
    (PROXY_PEOPLE_BASE, PROXY_GROUPS_BASE),
    (PEOPLE_BASE, GROUPS_BASE),
)


def role_dn(role: str, groups_base: str) -> str:
    """The DN a client uses to refer to a role, e.g. cn=platform:admin,ou=groups,..."""
    return f"cn={role},{groups_base}"


class TestKnoxLdapProxySearchWithRoleLookup(  # pylint: disable=too-many-public-methods
        TestKnoxLdapProxySearch):
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


    # ---- List searches: all roles are listed instead of the backend groups ----

    def assert_role_entries(self, entries) -> None:
        """Every entry is a groupOfNames role entry whose cn matches its DN."""
        for entry in entries:
            self.assertIn("groupOfNames", entry["objectClass"].values)
            self.assertEqual(self.rdn_values([entry]), entry["cn"].values)

    def test_list_all_roles_from_proxy_groups_base(self) -> None:
        """A group objectClass search from the proxy ou=groups base lists exactly the roles."""
        entries = self.search(PROXY_GROUPS_BASE, "(objectClass=groupOfNames)")
        self.assertEqual(ALL_ROLES, set(self.rdn_values(entries)))
        self.assertEqual(len(ALL_ROLES), len(entries))
        self.assert_role_entries(entries)

    def test_list_all_roles_entry_dns(self) -> None:
        """Role entries are placed under the proxy ou=groups base."""
        entries = self.search(PROXY_GROUPS_BASE, "(objectClass=groupOfNames)")
        self.assertEqual(
            {role_dn(role, PROXY_GROUPS_BASE).lower() for role in ALL_ROLES},
            {entry.entry_dn.lower() for entry in entries})

    def test_list_all_roles_by_cn_presence(self) -> None:
        """(cn=*) from the proxy ou=groups base lists all roles."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(cn=*)"))
        self.assertEqual(ALL_ROLES, set(groups))

    def test_list_all_roles_by_objectclass_presence(self) -> None:
        """(objectClass=*) from the proxy ou=groups base lists all roles."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(objectClass=*)"))
        self.assertEqual(ALL_ROLES, set(groups))

    def test_list_all_roles_from_proxy_root_with_group_filter(self) -> None:
        """Searching from the proxy root with a group objectClass filter lists all roles."""
        entries = self.search(PROXY_BASE_DN, "(objectClass=groupOfNames)")
        self.assertEqual(ALL_ROLES, set(self.rdn_values(entries)))
        self.assert_role_entries(entries)

    def test_list_all_roles_with_compound_group_filter(self) -> None:
        """A group objectClass condition inside an AND filter still lists all roles."""
        groups = self.rdn_values(
            self.search(PROXY_GROUPS_BASE, "(&(objectClass=groupOfNames)(cn=*))"))
        self.assertEqual(ALL_ROLES, set(groups))

    def test_list_all_roles_bypass_role_lookup(self) -> None:
        """With role lookup bypassed the backend groups are listed, not the roles."""
        entries = self.search(
            PROXY_GROUPS_BASE, "(objectClass=groupOfNames)", [ROLE_LOOKUP_BYPASS_CONTROL])
        groups = set(self.rdn_values(entries))
        self.assertTrue({"analyst", "scientist", "admin", "level1", "level2", "level3"} <= groups)
        self.assertFalse(any(group.startswith("platform:") for group in groups))

    def test_user_search_from_proxy_root_is_not_a_list_search(self) -> None:
        """A non-group filter from the proxy root is not answered with the role list."""
        users = self.rdn_values(self.search(PROXY_BASE_DN, "(uid=sam)"))
        self.assertEqual(["sam"], users)

    # ---- Reverse role lookup: (memberOf=<role DN>) finds the users that hold the role ----

    def assert_role_members(self, role: str, expected_users: set[str]) -> None:
        """Searching users by memberOf=<role DN> returns exactly the expected users,
        whichever DN form (proxy or backend) the client uses."""
        for people_base, groups_base in BASE_PAIRS:
            with self.subTest(role=role, base=people_base):
                users = self.rdn_values(
                    self.search(people_base, f"(memberOf={role_dn(role, groups_base)})"))
                self.assertEqual(expected_users, set(users))

    def test_reverse_lookup_role_assigned_to_group(self) -> None:
        """A role assigned to a group resolves to that group's members."""
        self.assert_role_members("platform:analyst", {"sam", "tom"})

    def test_reverse_lookup_role_with_single_member(self) -> None:
        """A role whose group has one member resolves to that member."""
        self.assert_role_members("platform:scientist", {"sam"})

    def test_reverse_lookup_role_assigned_to_user_and_group(self) -> None:
        """A role assigned to both a user and a group resolves to the user once."""
        self.assert_role_members("platform:admin", {"admin"})

    def test_reverse_lookup_nested_group_roles(self) -> None:
        """Roles of nested groups resolve to members of the nested groups, recursively."""
        for role in ("platform:level1", "platform:level2", "platform:level3"):
            self.assert_role_members(role, {"recursiveUser"})

    def test_reverse_lookup_role_inside_and_filter(self) -> None:
        """A memberOf role condition combined with another condition in an AND filter."""
        analyst = role_dn("platform:analyst", PROXY_GROUPS_BASE)
        users = self.rdn_values(self.search(
            PROXY_PEOPLE_BASE, f"(&(objectClass=inetOrgPerson)(memberOf={analyst})(uid=tom))"))
        self.assertEqual(["tom"], users)

    def test_reverse_lookup_two_roles_inside_or_filter(self) -> None:
        """An OR of memberOf role conditions returns the members of either role."""
        users = self.rdn_values(self.search(
            PROXY_PEOPLE_BASE,
            f"(|(memberOf={role_dn('platform:scientist', PROXY_GROUPS_BASE)})"
            f"(memberOf={role_dn('platform:admin', PROXY_GROUPS_BASE)}))"))
        self.assertEqual({"sam", "admin"}, set(users))

    def test_reverse_lookup_two_roles_inside_and_filter(self) -> None:
        """An AND of memberOf role conditions returns only users holding both roles."""
        users = self.rdn_values(self.search(
            PROXY_PEOPLE_BASE,
            f"(&(memberOf={role_dn('platform:analyst', PROXY_GROUPS_BASE)})"
            f"(memberOf={role_dn('platform:scientist', PROXY_GROUPS_BASE)}))"))
        self.assertEqual({"sam"}, set(users))

    def test_reverse_lookup_not_filter(self) -> None:
        """Negating a memberOf role condition excludes the role's members."""
        users = self.rdn_values(self.search(
            PROXY_PEOPLE_BASE,
            "(&(objectClass=inetOrgPerson)"
            f"(!(memberOf={role_dn('platform:analyst', PROXY_GROUPS_BASE)})))"))
        self.assertNotIn("sam", users)
        self.assertNotIn("tom", users)
        self.assertIn("admin", users)

    def test_reverse_lookup_unknown_role_matches_nothing(self) -> None:
        """A role that does not exist has no members."""
        users = self.search(
            PROXY_PEOPLE_BASE,
            f"(memberOf={role_dn('platform:doesnotexist', PROXY_GROUPS_BASE)})")
        self.assertEqual([], users)

    def test_reverse_lookup_role_dn_not_resolved_when_bypassed(self) -> None:
        """With role lookup bypassed a role DN is not rewritten, so it matches no user."""
        users = self.search(
            PROXY_PEOPLE_BASE,
            f"(memberOf={role_dn('platform:analyst', PROXY_GROUPS_BASE)})",
            [ROLE_LOOKUP_BYPASS_CONTROL])
        self.assertEqual([], users)

    def test_reverse_lookup_group_dn_when_bypassed(self) -> None:
        """With role lookup bypassed a backend group DN resolves to the group's members."""
        users = self.rdn_values(self.search(
            PROXY_PEOPLE_BASE,
            f"(memberOf={role_dn('analyst', PROXY_GROUPS_BASE)})",
            [ROLE_LOOKUP_BYPASS_CONTROL]))
        self.assertEqual({"sam", "tom"}, set(users))


# Drop the bare base-class reference so pytest's unittest collection doesn't also run
# it here as its own top-level test case; the subclass above still inherits its tests.
del TestKnoxLdapProxySearch


if __name__ == "__main__":
    unittest.main()
