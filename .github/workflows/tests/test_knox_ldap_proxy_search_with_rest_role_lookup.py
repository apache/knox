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

"""Integration tests for the embedded Knox LDAP proxy with the REST roles lookup strategy and
gateway.ldap.interceptor.roleslookup.lowercaseIds=true.

The roles come from the roles-api mock (compose/role-lookup-rest/mock_roles_api.py), which
serves the same role-lookup/roles.json the file strategy reads. So every role lookup test of
TestKnoxLdapProxySearchWithRoleLookup runs again here, covering roles lookup, role listing
(paged by the mock) and reverse role lookups through the REST API.

Like a real roles backend, the mock matches user ids case-sensitively. The directory uid
recursiveUser is mixed case while its role platform:deployer is assigned to the lowercase,
login-typed id recursiveuser; it only matches because the gateway lowercases the user id it
sends to the roles lookup. The gateway lowercases the group names too; the demo LDAP group
names are lowercase already, so the inherited group tests check that this changes nothing for
them, and the unit tests cover mixed-case group names.
"""

from __future__ import annotations

import unittest

from test_knox_ldap_proxy_search_with_role_lookup import TestKnoxLdapProxySearchWithRoleLookup


class TestKnoxLdapProxySearchWithRestRoleLookup(TestKnoxLdapProxySearchWithRoleLookup):
    """Verify the role lookup features against the REST strategy, with the user id
    and group names lowercased before they are sent to the roles lookup."""

    def test_user_role_lookup_keeps_user_id_case(self) -> None:
        """Not applicable: this gateway lowercases the user id, see the tests below."""
        self.skipTest("gateway.ldap.interceptor.roleslookup.lowercaseIds is true")

    def test_user_role_lookup_lowercases_user_id(self) -> None:
        """The mixed-case directory uid recursiveUser is sent as recursiveuser, so it gets the
        role assigned to the lowercase id on top of the roles of its groups."""
        roles = self.user_roles("recursiveUser")
        self.assertIn("platform:deployer", roles)
        self.assertIn("platform:level1", roles)

    def test_user_role_lookup_lowercases_user_id_whatever_the_filter_case(self) -> None:
        """The user id comes from the returned entry, not the filter: any filter case that
        finds the user gets the same roles."""
        for uid_filter_value in ("recursiveuser", "RECURSIVEUSER", "RecursiveUser"):
            with self.subTest(uid=uid_filter_value):
                self.assertIn("platform:deployer", self.user_roles(uid_filter_value))

    def test_user_role_lookup_does_not_change_returned_uid(self) -> None:
        """Only the user id sent to the roles lookup is lowercased; the returned entry keeps
        the directory uid."""
        uid, _ = self.search_user("recursiveuser")
        self.assertEqual("recursiveUser", uid)


# Drop the base-class reference so pytest's unittest collection doesn't also run it here as its
# own top-level test case; the subclass above still inherits its tests.
del TestKnoxLdapProxySearchWithRoleLookup


if __name__ == "__main__":
    unittest.main()
