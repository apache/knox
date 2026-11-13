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

The demo backend's system (bind) password is supplied to the proxy via a
credential-store alias (gateway.ldap.interceptor.demoldap.systemPassword=
${ALIAS=gateway_ldap_demoldap_system_password}, seeded in gateway.sh). The proxy
must resolve that alias to bind to the demo LDAP; if resolution regressed the
literal alias string would be used as the password and every search below would
fail on a bind error. This suite therefore also covers KNOX-3497.

gateway.sh creates that alias only after Knox has failed to find it and started
retrying the lookup, so the searches below also prove that the retry picked up
the late alias (KNOX-3513; see test_knox_ldap_backend_password_alias_retry.py).
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
BIND_DN = f"uid=bind,{PROXY_PEOPLE_BASE}"
BIND_PASSWORD = "bind-password"


def knox_host() -> str:
    """Derive the Knox host from KNOX_GATEWAY_URL (defaults to localhost)."""
    url = os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")
    return urlparse(url).hostname or "localhost"


class TestKnoxLdapProxySearch(unittest.TestCase):  # pylint: disable=too-many-public-methods
    """Verify general search requests are proxied to the demo LDAP backend."""

    # The proxy can also be configured to run the attribute normalization interceptor
    # (gateway.ldap.interceptor.normalizer.interceptorType=attributenormalizer), which
    # lowercases the values of case-insensitive attributes in the entries it returns.
    # The demo LDAP stores mixed-case values (e.g. cn "Sam Peterson", uid "recursiveUser"),
    # so the searches below also verify the normalized values (including DN-valued
    # attributes, objectClass and the entry DN), and that requested-attribute
    # selection still applies to normalized entries. Test classes set the
    # is_attribute_normalization_interceptor_configured field if knox is configured with
    # the attribute normalization interceptor
    is_attribute_normalization_interceptor_configured = True

    def setUp(self) -> None:
        # Self-signed dev certificate in CI: connect over TLS but skip validation.
        tls = ldap3.Tls(validate=ssl.CERT_NONE)
        server = ldap3.Server(
            knox_host(), port=KNOX_LDAP_PORT, use_ssl=True, tls=tls, get_info=ldap3.NONE
        )
        self.connection = ldap3.Connection(
            server, user=BIND_DN, password=BIND_PASSWORD, auto_bind=True, raise_exceptions=True
        )

    def tearDown(self) -> None:
        self.connection.unbind()

    def search(
        self,
        base: str,
        ldap_filter: str,
        controls: list[tuple] = None,
        attributes=ldap3.ALL_ATTRIBUTES,
    ) -> list[ldap3.Entry]:
        """Run a subtree search and return the entries.
           Re-running search will invalidate the entries."""

        self.connection.search(
            base,
            ldap_filter,
            search_scope=ldap3.SUBTREE,
            attributes=attributes,
            controls=controls)
        return self.connection.entries

    def search_one(
        self, base: str, ldap_filter: str, attributes
    ) -> ldap3.Entry:
        """Run a subtree search expected to match exactly one entry and return it."""
        entries = self.search(base, ldap_filter, attributes=attributes)
        self.assertEqual(1, len(entries), f"expected one entry for {ldap_filter}")
        return entries[0]

    def attribute_names(self, entry: ldap3.Entry) -> set[str]:
        """The (lower-cased) names of the attributes returned for an entry."""
        return {name.lower() for name in entry.entry_attributes_as_dict}

    def rdn_values(self, entries: list[ldap3.Entry]) -> list[str]:
        """Extract the RDN value of each entry."""
        values = []
        for entry in entries:
            # entry_dn looks like "uid=guest,ou=people,..." or "cn=level1,ou=groups,..."
            first_rdn = entry.entry_dn.split(",", 1)[0]
            values.append(first_rdn.split("=", 1)[1])
        return values

    def test_anonymous_search_rejected(self) -> None:
        """Anonymous search not allowed when bind user configured."""
        # Self-signed dev certificate in CI: connect over TLS but skip validation.
        tls = ldap3.Tls(validate=ssl.CERT_NONE)
        server = ldap3.Server(
            knox_host(), port=KNOX_LDAP_PORT, use_ssl=True, tls=tls, get_info=ldap3.NONE
        )
        with self.assertRaises(ldap3.core.exceptions.LDAPInvalidCredentialsResult):
            try:
                connection = ldap3.Connection(
                    server, auto_bind=ldap3.AUTO_BIND_NONE, raise_exceptions=True
                )
                connection.bind()
            finally:
                if connection.bound:
                    connection.unbind()

    def test_search_all_users_by_objectclass(self) -> None:
        """All inetOrgPerson entries under ou=people are returned."""
        users = self.rdn_values(self.search(PEOPLE_BASE, "(objectClass=inetOrgPerson)"))
        expected_users = ["guest", "admin", "sam", "tom", "recursiveUser"]
        if self.is_attribute_normalization_interceptor_configured:
            expected_users = [string.lower() for string in expected_users]
        for expected in expected_users:
            self.assertIn(expected, users)

    def test_search_all_groups_by_objectclass(self) -> None:
        """All groupOfNames entries under ou=groups are returned."""
        groups = self.rdn_values(self.search(GROUPS_BASE, "(objectClass=groupOfNames)"))
        for expected in ("analyst", "scientist", "admin", "level1", "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard(self) -> None:
        """A cn wildcard filter returns only the matching groups."""
        groups = self.rdn_values(self.search(GROUPS_BASE, "(cn=level*)"))
        self.assertEqual({"level1", "level2", "level3"}, set(groups))

    def test_search_user_by_uid(self) -> None:
        """A single user can still be looked up by uid."""
        users = self.rdn_values(self.search(PEOPLE_BASE, "(uid=sam)"))
        self.assertIn("sam", users)

    def test_search_all_users_by_objectclass_proxy_dn(self) -> None:
        """All inetOrgPerson entries under ou=people are returned."""
        users = self.rdn_values(self.search(PROXY_PEOPLE_BASE, "(objectClass=inetOrgPerson)"))
        expected_users = ["guest", "admin", "sam", "tom", "recursiveUser"]
        if self.is_attribute_normalization_interceptor_configured:
            expected_users = [string.lower() for string in expected_users]
        for expected in expected_users:
            self.assertIn(expected, users)

    def test_search_all_groups_by_objectclass_proxy_dn(self) -> None:
        """All groupOfNames entries under ou=groups are returned."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(objectClass=groupOfNames)"))
        for expected in ("analyst", "scientist", "admin", "level1", "level2", "level3"):
            self.assertIn(expected, groups)

    def test_search_groups_by_cn_wildcard_proxy_dn(self) -> None:
        """A cn wildcard filter returns only the matching groups."""
        groups = self.rdn_values(self.search(PROXY_GROUPS_BASE, "(cn=level*)"))
        self.assertEqual({"level1", "level2", "level3"}, set(groups))

    def test_search_user_by_uid_proxy_dn(self) -> None:
        """A single user can still be looked up by uid."""
        users = self.rdn_values(self.search(PROXY_PEOPLE_BASE, "(uid=sam)"))
        self.assertIn("sam", users)

    # ---- Returning attributes ----
    #
    # These exercise the attribute selection honored by UserSearchInterceptor's
    # getReturningAttributes(): a client may ask for no attributes (OID "1.1"),
    # all user attributes ("*"), or an explicit list, and only those come back.

    def test_search_returns_all_user_attributes(self) -> None:
        """Requesting all user attributes ("*") returns the user's attributes."""
        entry = self.search_one(PEOPLE_BASE, "(uid=sam)", ldap3.ALL_ATTRIBUTES)
        self.assertTrue(
            {"uid", "cn", "sn", "mail", "objectclass"} <= self.attribute_names(entry),
            self.attribute_names(entry))

    def test_search_returns_only_requested_attribute(self) -> None:
        """Requesting a single attribute returns that attribute and no other user
        attribute (e.g. mail and sn are withheld)."""
        entry = self.search_one(PEOPLE_BASE, "(uid=sam)", ["cn"])
        names = self.attribute_names(entry)
        self.assertIn("cn", names)
        self.assertNotIn("mail", names)
        self.assertNotIn("sn", names)
        self.assertNotIn("uid", names)

    def test_search_returns_only_requested_attributes(self) -> None:
        """Requesting an explicit list returns exactly those attributes."""
        entry = self.search_one(PEOPLE_BASE, "(uid=sam)", ["uid", "mail"])
        names = self.attribute_names(entry)
        self.assertIn("uid", names)
        self.assertIn("mail", names)
        self.assertNotIn("cn", names)
        self.assertNotIn("sn", names)

    def test_search_no_attributes_returns_entry_without_attributes(self) -> None:
        """Requesting no attributes (OID "1.1") still returns the matching entry
        but with none of its attributes."""
        entry = self.search_one(PEOPLE_BASE, "(uid=sam)", ldap3.NO_ATTRIBUTES)
        self.assertEqual("sam", entry.entry_dn.split(",", 1)[0].split("=", 1)[1])
        self.assertEqual(set(), self.attribute_names(entry))

    def test_search_returns_only_requested_attribute_proxy_dn(self) -> None:
        """Attribute selection is honored when searching under the proxy base DN."""
        entry = self.search_one(PROXY_PEOPLE_BASE, "(uid=sam)", ["cn"])
        names = self.attribute_names(entry)
        self.assertIn("cn", names)
        self.assertNotIn("mail", names)

    def test_search_no_attributes_returns_entry_without_attributes_proxy_dn(self) -> None:
        """No-attribute search is honored when searching under the proxy base DN."""
        entry = self.search_one(PROXY_PEOPLE_BASE, "(uid=sam)", ldap3.NO_ATTRIBUTES)
        self.assertEqual("sam", entry.entry_dn.split(",", 1)[0].split("=", 1)[1])
        self.assertEqual(set(), self.attribute_names(entry))

    # ---- Attribute Normalization: interceptor lowercases attributes and dn ----

    def test_case_insensitive_attribute_values_are_lowercased(self) -> None:
        """Mixed-case values of case-insensitive attributes are returned lowercased."""
        if not self.is_attribute_normalization_interceptor_configured:
            self.skipTest("AttributeNormalizationInterceptor not configured")
        entries = self.search(PROXY_PEOPLE_BASE, "(uid=sam)")
        self.assertEqual(1, len(entries))
        entry = entries[0]
        self.assertEqual(["sam peterson"], entry["cn"].values)
        self.assertEqual(["peterson"], entry["sn"].values)
        self.assertEqual(["sam"], entry["uid"].values)

        entries = self.search(PROXY_PEOPLE_BASE, "(uid=recursiveUser)")
        self.assertEqual(1, len(entries))
        entry = entries[0]
        self.assertEqual(["recursiveuser"], entry["uid"].values)
        self.assertEqual(["recursive"], entry["cn"].values)
        self.assertEqual(["user"], entry["sn"].values)

    def test_entry_dn_is_lowercased(self) -> None:
        """The entry DN is lowercased along with the attribute values."""
        if not self.is_attribute_normalization_interceptor_configured:
            self.skipTest("AttributeNormalizationInterceptor not configured")
        entries = self.search(PROXY_PEOPLE_BASE, "(uid=recursiveUser)")
        self.assertEqual(1, len(entries))
        self.assertEqual(f"uid=recursiveUser,{PROXY_PEOPLE_BASE}".lower(), entries[0].entry_dn)

    def test_object_class_is_lowercased(self) -> None:
        """objectClass values are lowercased."""
        if not self.is_attribute_normalization_interceptor_configured:
            self.skipTest("AttributeNormalizationInterceptor not configured")
        entries = self.search(PROXY_PEOPLE_BASE, "(uid=sam)")
        self.assertEqual(1, len(entries))
        values = entries[0]["objectClass"].values
        self.assertIn("inetorgperson", values)
        self.assertNotIn("inetOrgPerson", values)

    def test_dn_valued_attributes_are_lowercased(self) -> None:
        """DN-valued attributes such as member are lowercased."""
        if not self.is_attribute_normalization_interceptor_configured:
            self.skipTest("AttributeNormalizationInterceptor not configured")
        entries = self.search(PROXY_GROUPS_BASE, "(cn=level1)")
        self.assertEqual(1, len(entries))
        members = entries[0]["member"].values
        self.assertEqual(1, len(members))
        self.assertEqual(members[0].lower(), members[0])
        self.assertTrue(
            members[0].startswith("uid=recursiveuser,ou=people,"),
            f"unexpected member value: {members[0]}",
        )

    def test_requested_attributes_are_still_honored(self) -> None:
        """Only the requested attributes are returned for a normalized entry."""
        if not self.is_attribute_normalization_interceptor_configured:
            self.skipTest("AttributeNormalizationInterceptor not configured")
        entries = self.search(PROXY_PEOPLE_BASE, "(uid=sam)", attributes=["uid", "mail"])
        self.assertEqual(1, len(entries))
        entry = entries[0]
        self.assertEqual(["sam"], entry["uid"].values)
        self.assertEqual(["sam@example.org"], entry["mail"].values)
        returned = {name.lower() for name in entry.entry_attributes}
        self.assertEqual({"uid", "mail"}, returned)


if __name__ == "__main__":
    unittest.main()
