/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.ldap;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LdapUtilsTest {
    private SchemaManager schemaManager;

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    @Test
    public void testIsGroupEntryWithGroupOfNames() throws Exception {
        final Entry entry = createEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org", "groupOfNames");
        assertTrue(LdapUtils.isGroupEntry(entry));
    }

    @Test
    public void testIsGroupEntryWithGroupOfUniqueNames() throws Exception {
        final Entry entry = createEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org", "groupOfUniqueNames");
        assertTrue(LdapUtils.isGroupEntry(entry));
    }

    @Test
    public void testIsGroupEntryWithUserObjectClass() throws Exception {
        final Entry entry = createEntry("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org", "inetOrgPerson");
        assertFalse(LdapUtils.isGroupEntry(entry));
    }

    @Test
    public void testIsGroupEntryWithNoObjectClass() throws Exception {
        final Entry entry = new DefaultEntry(schemaManager, "cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertFalse(LdapUtils.isGroupEntry(entry));
    }

    @Test
    public void testIsUserEntryWithInetOrgPerson() throws Exception {
        final Entry entry = createEntry("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org", "inetOrgPerson");
        assertTrue(LdapUtils.isUserEntry(entry));
    }

    @Test
    public void testIsUserEntryWithPerson() throws Exception {
        final Entry entry = createEntry("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org", "person");
        assertTrue(LdapUtils.isUserEntry(entry));
    }

    @Test
    public void testIsUserEntryWithOrganizationalPerson() throws Exception {
        final Entry entry = createEntry("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org", "organizationalPerson");
        assertTrue(LdapUtils.isUserEntry(entry));
    }

    @Test
    public void testIsUserEntryWithGroupObjectClass() throws Exception {
        final Entry entry = createEntry("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org", "groupOfNames");
        assertFalse(LdapUtils.isUserEntry(entry));
    }

    @Test
    public void testIsUserEntryWithNoObjectClass() throws Exception {
        final Entry entry = new DefaultEntry(schemaManager, "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        assertFalse(LdapUtils.isUserEntry(entry));
    }

    @Test
    public void testExtractUsernameFromDnWithUidRdn() throws Exception {
        final Dn dn = new Dn("uid=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        assertEquals("alice", LdapUtils.extractUsernameFromDn(dn));
    }

    @Test
    public void testExtractUsernameFromDnWithNonUidRdn() throws Exception {
        final Dn dn = new Dn("cn=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        assertNull(LdapUtils.extractUsernameFromDn(dn));
    }

    @Test
    public void testExtractUsernameFromDnWithNullDn() {
        assertNull(LdapUtils.extractUsernameFromDn(null));
    }

    @Test
    public void testExtractUsernameFromDnWithEmptyDn() {
        assertNull(LdapUtils.extractUsernameFromDn(new Dn()));
    }

    @Test
    public void testExtractUsernameFromEntryUsesFirstMatchingAttribute() throws Exception {
        final Entry entry = new DefaultEntry(schemaManager);
        entry.add("uid", "alice");
        entry.add("cn", "Alice Smith");
        assertEquals("alice", LdapUtils.extractUsernameFromEntry(entry, "uid", "cn"));
    }

    @Test
    public void testExtractUsernameFromEntryFallsBackToLaterAttribute() throws Exception {
        final Entry entry = new DefaultEntry(schemaManager);
        entry.add("cn", "alice");
        assertEquals("alice", LdapUtils.extractUsernameFromEntry(entry, "uid", "cn"));
    }

    @Test
    public void testExtractUsernameFromEntryWithNoMatchingAttribute() throws Exception {
        final Entry entry = new DefaultEntry(schemaManager);
        entry.add("sn", "Smith");
        assertNull(LdapUtils.extractUsernameFromEntry(entry, "uid", "cn"));
    }

    @Test
    public void testExtractGroupNameWithCnRdn() throws Exception {
        final Dn dn = new Dn("cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org");
        assertEquals("engineering", LdapUtils.extractGroupName(dn));
    }

    @Test
    public void testExtractGroupNameWithNonCnRdn() throws Exception {
        final Dn dn = new Dn("ou=groups,dc=hadoop,dc=apache,dc=org");
        assertNull(LdapUtils.extractGroupName(dn));
    }

    @Test
    public void testExtractGroupNameWithEmptyDn() {
        assertNull(LdapUtils.extractGroupName(new Dn()));
    }

    private Entry createEntry(final String dn, final String objectClass) throws Exception {
        final Entry entry = new DefaultEntry(schemaManager, dn);
        entry.add("objectClass", objectClass);
        return entry;
    }
}
