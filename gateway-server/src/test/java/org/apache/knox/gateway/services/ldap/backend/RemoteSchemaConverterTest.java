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
package org.apache.knox.gateway.services.ldap.backend;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemoteSchemaConverterTest {
    private static final String PROXY_BASE_DN = "dc=proxy,dc=com";
    private static final String PROXY_USER_SEARCH_BASE = "ou=people,dc=proxy,dc=com";
    private static final String PROXY_GROUP_SEARCH_BASE = "ou=groups,dc=proxy,dc=com";
    private static final String REMOTE_BASE_DN = "dc=hadoop,dc=apache,dc=org";
    private static final String REMOTE_USER_SEARCH_BASE = "ou=people,dc=hadoop,dc=apache,dc=org";
    private static final String REMOTE_GROUP_SEARCH_BASE = "ou=groups,dc=hadoop,dc=apache,dc=org";

    private SchemaManager schemaManager;

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    @Test
    public void testConvertRemoteEntryToProxyEntrySkipsSensitiveAttributes() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "uid=alice," + REMOTE_USER_SEARCH_BASE);
        source.add("objectClass", "person");
        source.add("uid", "alice");
        source.add("userPassword", "secret");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        assertNull("Sensitive attribute must never be copied to the proxy entry", proxyEntry.get("userPassword"));
        assertEquals("alice", proxyEntry.get("uid").getString());
    }

    @Test
    public void testConvertRemoteEntryToProxyEntryPreservesRemoteDnWhenDnMappingDisabled() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "uid=alice," + REMOTE_USER_SEARCH_BASE);
        source.add("objectClass", "person");
        source.add("uid", "alice");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        assertEquals(source.getDn().getName(), proxyEntry.getDn().getName());
    }

    @Test
    public void testConvertRemoteEntryToProxyEntryRewritesDnWhenDnMappingEnabled() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", true);
        final Entry source = new DefaultEntry(schemaManager, "uid=alice," + REMOTE_USER_SEARCH_BASE);
        source.add("objectClass", "person");
        source.add("uid", "alice");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        assertEquals("uid=alice," + PROXY_USER_SEARCH_BASE, proxyEntry.getDn().getName());
    }

    @Test
    public void testConvertRemoteEntryToProxyEntryRemapsUserIdentifierAttributeToUid() throws Exception {
        final RemoteSchemaConverter converter = createConverter("sAMAccountName", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "cn=alice," + REMOTE_USER_SEARCH_BASE);
        source.add("objectClass", "person");
        source.add("sAMAccountName", "alice");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        assertEquals("alice", proxyEntry.get("uid").getString());
        assertTrue(proxyEntry.get("objectclass").contains("inetOrgPerson"));
    }

    @Test
    public void testConvertRemoteEntryToProxyEntrySkipsUidRemappingWhenIdentifierIsAlreadyUid() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "uid=alice," + REMOTE_USER_SEARCH_BASE);
        source.add("objectClass", "person");
        source.add("uid", "alice");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        assertEquals("uid should not be duplicated when it is already the identifier attribute",
                1, proxyEntry.get("uid").size());
    }

    @Test
    public void testConvertRemoteEntryToProxyEntrySwapsGroupObjectClass() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        source.add("objectClass", "posixGroup");
        source.add("cn", "engineering");

        final Entry proxyEntry = converter.convertRemoteEntryToProxyEntry(source, schemaManager);

        final Attribute objectClass = proxyEntry.get("objectclass");
        assertTrue(objectClass.contains("groupofnames"));
        assertFalse(objectClass.contains("posixGroup"));
    }

    @Test
    public void testCopyAttributeSkipsDuplicateValues() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        source.add("description", "team");
        final Entry target = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        target.add("description", "team");

        converter.copyAttribute(source, target, "description", schemaManager);

        assertEquals(1, target.get("description").size());
    }

    @Test
    public void testCopyAttributeDoesNotRewriteNonDnValuedAttributes() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", true);
        final String description = "Team based at " + REMOTE_BASE_DN;
        final Entry source = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        source.add("description", description);
        final Entry target = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);

        converter.copyAttribute(source, target, "description", schemaManager);

        assertEquals("Non-DN-valued attribute values must be copied verbatim, never rewritten",
                description, target.get("description").getString());
    }

    @Test
    public void testCopyAttributeRewritesDnValuedAttributeWhenDnMappingEnabled() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", true);
        final Entry source = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        source.add("member", "uid=alice," + REMOTE_USER_SEARCH_BASE);
        final Entry target = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);

        converter.copyAttribute(source, target, "member", schemaManager);

        assertEquals("uid=alice," + PROXY_USER_SEARCH_BASE, target.get("member").getString());
    }

    @Test
    public void testCopyAttributeDoesNotRewriteDnValuedAttributeWhenDnMappingDisabled() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);
        final Entry source = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);
        source.add("member", "uid=alice," + REMOTE_USER_SEARCH_BASE);
        final Entry target = new DefaultEntry(schemaManager, "cn=engineering," + REMOTE_GROUP_SEARCH_BASE);

        converter.copyAttribute(source, target, "member", schemaManager);

        assertEquals("uid=alice," + REMOTE_USER_SEARCH_BASE, target.get("member").getString());
    }

    @Test
    public void testConvertProxyDnToRemoteDnWithNull() {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", true);
        assertNull(converter.convertProxyDnToRemoteDn(null));
    }

    @Test
    public void testConvertProxyDnToRemoteDnHandlesRegexMetacharactersInConfiguredBase() {
        // The configured bases below contain regex metacharacters. If they were used as raw
        // regex patterns instead of literal (Pattern.quote-d) strings, this would either fail
        // to compile or silently mismatch.
        final RemoteSchemaConverter converter = new RemoteSchemaConverter(
                PROXY_BASE_DN, "ou=people(a+b),dc=proxy,dc=com", PROXY_GROUP_SEARCH_BASE,
                REMOTE_BASE_DN, "ou=people(a+b),dc=hadoop,dc=apache,dc=org", REMOTE_GROUP_SEARCH_BASE,
                "uid", "person", "posixGroup", true);

        final String proxyDn = "uid=alice,ou=people(a+b),dc=proxy,dc=com";
        final String remoteDn = converter.convertProxyDnToRemoteDn(proxyDn);

        assertEquals("uid=alice,ou=people(a+b),dc=hadoop,dc=apache,dc=org", remoteDn);
    }

    @Test
    public void testConvertProxyDnToRemoteDnIsCaseInsensitive() {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", true);
        final String remoteDn = converter.convertProxyDnToRemoteDn("UID=alice,OU=People,DC=Proxy,DC=Com");
        assertEquals("UID=alice,ou=people,dc=hadoop,dc=apache,dc=org", remoteDn);
    }

    @Test
    public void testConvertProxyFilterToRemoteFilterMapsUidAttribute() throws Exception {
        final RemoteSchemaConverter converter = createConverter("sAMAccountName", "person", "posixGroup", false);

        final String result = converter.convertProxyFilterToRemoteFilter("(uid=alice)", schemaManager);

        assertEquals("(sAMAccountName=alice)", result);
    }

    @Test
    public void testConvertProxyFilterToRemoteFilterStripsAnnotationsAndExtraWhitespace() throws Exception {
        final RemoteSchemaConverter converter = createConverter("uid", "person", "posixGroup", false);

        final String result = converter.convertProxyFilterToRemoteFilter("(uid:[1.2.3.4]=  alice)", schemaManager);

        assertEquals("(uid=alice)", result);
    }

    private RemoteSchemaConverter createConverter(String remoteUserIdentifierAttribute,
                                                    String remoteUserObjectClass,
                                                    String remoteGroupObjectClass,
                                                    boolean dnMappingEnabled) {
        return new RemoteSchemaConverter(
                PROXY_BASE_DN, PROXY_USER_SEARCH_BASE, PROXY_GROUP_SEARCH_BASE,
                REMOTE_BASE_DN, REMOTE_USER_SEARCH_BASE, REMOTE_GROUP_SEARCH_BASE,
                remoteUserIdentifierAttribute, remoteUserObjectClass, remoteGroupObjectClass, dnMappingEnabled);
    }
}
