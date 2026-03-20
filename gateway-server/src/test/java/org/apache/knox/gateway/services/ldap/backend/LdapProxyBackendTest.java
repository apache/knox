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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.factory.JdbmPartitionFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LdapProxyBackendTest {
    private static Map<String, String> ldapBackendConfig;

    private static TcpTransport transport;
    private static DirectoryService directoryService;
    private static LdapServer ldapServer;
    private static SchemaManager schemaManager;

    private LdapProxyBackend ldapProxyBackend;

    @BeforeClass
    public static void setupBeforeClass() throws Exception {
        transport = new TcpTransport(0);

        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        // Use same schema for test that we use for code
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        String instanceDirectory = System.getProperty("java.io.tmpdir", "/tmp") + "/server-work-" + UUID.randomUUID().toString();
        InstanceLayout instanceLayout = new InstanceLayout(instanceDirectory);
        directoryService.setInstanceLayout(instanceLayout);

        File workingDirectory = directoryService.getInstanceLayout().getPartitionsDirectory();
        LdifPartition ldifPartition = new LdifPartition(schemaManager, directoryService.getDnFactory());
        ldifPartition.setPartitionPath((new File(workingDirectory, "schema")).toURI());
        SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
        schemaPartition.setWrappedPartition(ldifPartition);
        directoryService.setSchemaPartition(schemaPartition);

        PartitionFactory partitionFactory = new JdbmPartitionFactory();
        Partition systemPartition = partitionFactory.createPartition(
                schemaManager, directoryService.getDnFactory(),
                "system", "ou=system", 500,
                new File(directoryService.getInstanceLayout().getPartitionsDirectory(), "system"));
        partitionFactory.addIndex(systemPartition, "objectClass", 100);
        directoryService.setSystemPartition(systemPartition);
        Partition partition = partitionFactory.createPartition(
                schemaManager, directoryService.getDnFactory(),
                "people", "dc=hadoop,dc=apache,dc=org", 500,
                directoryService.getInstanceLayout().getInstanceDirectory());
        directoryService.addPartition(partition);

        // Start the DirectoryService
        directoryService.startup();

        // load test data from ldif file
        File ldifFile = new File(
                LdapProxyBackendTest.class.getResource( "/ldap-proxy-backend-test.ldif" )
                        .toURI());
        CoreSession session = directoryService.getAdminSession();
        LdifFileLoader lfl = new LdifFileLoader(session, ldifFile, null);
        lfl.execute();

        // Create and start the LDAP server
        ldapServer = new LdapServer();
        ldapServer.setTransports(transport);
        ldapServer.setDirectoryService(directoryService);
        ldapServer.start();

        // Setup common backend config values for tests
        ldapBackendConfig = Map.of(
                "baseDn", "dc=hadoop,dc=apache,dc=org",
                "remoteBaseDn", "dc=hadoop,dc=apache,dc=org",
                "url", "ldap://localhost:" + transport.getAcceptor().getLocalAddress().getPort(),
                "systemUsername", "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org",
                "systemPassword", "guest-password",
                "userSearchBase", "ou=people,dc=hadoop,dc=apache,dc=org",
                "groupSearchBase", "ou=groups,dc=hadoop,dc=apache,dc=org");
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if(ldapServer != null) {
            ldapServer.stop();
        }
        if (directoryService != null) {
            directoryService.shutdown();
        }
    }

    @Before
    public void setUp() throws Exception {
        ldapProxyBackend = new LdapProxyBackend();
    }

    @After
    public void tearDown() throws Exception {
        ldapProxyBackend.close();
    }

    @Test
    public void testGetUserByDefaultUserSearchFilter() throws Exception {
        // default searches by uid and uses group search for membership
        ldapProxyBackend.initialize(ldapBackendConfig);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        assertEquals("ldaptest1", entry.get("uid").getString());
        assertEquals("TestCn1", entry.get("cn").getString());
        assertEquals("ldaptest1@example.com", entry.get("mail").getString());
        assertEquals("Test user ldaptest1", entry.get("description").getString());
        assertNull(entry.get("sAMAccountName"));
        assertEquals(2, entry.get("memberOf").size());
        Set<String> expectedMemberOf = Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org");
        Set<String> foundMemberOf = new HashSet<>(2);
        Iterator<Value> memberOfs = entry.get("memberOf").iterator();
        while (memberOfs.hasNext()) {
            foundMemberOf.add(memberOfs.next().getString());
        }
        assertEquals(expectedMemberOf, foundMemberOf);
    }

    @Test
    public void testGetUserNotFound() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);

        Entry entry = ldapProxyBackend.getUser("nouser", schemaManager);
        assertNull(entry);
    }

    @Test
    public void testGetUserByUID() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "uid");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        assertEquals("ldaptest1", entry.get("uid").getString());
        assertEquals("TestCn1", entry.get("cn").getString());
        assertEquals("ldaptest1@example.com", entry.get("mail").getString());
        assertEquals("Test user ldaptest1", entry.get("description").getString());
        assertNull(entry.get("sAMAccountName"));
        assertEquals(2, entry.get("memberOf").size());
        Set<String> expectedMemberOf = Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org");
        Set<String> foundMemberOf = new HashSet<>(2);
        Iterator<Value> memberOfs = entry.get("memberOf").iterator();
        while (memberOfs.hasNext()) {
            foundMemberOf.add(memberOfs.next().getString());
        }
        assertEquals(expectedMemberOf, foundMemberOf);
    }

    @Test
    public void testGetUserByCN() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "cn");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("TestCn1", schemaManager);
        assertEquals("TestCn1", entry.get("uid").getString());
        assertEquals("TestCn1", entry.get("cn").getString());
        assertEquals("ldaptest1@example.com", entry.get("mail").getString());
        assertEquals("Test user ldaptest1", entry.get("description").getString());
        assertNull(entry.get("sAMAccountName"));
        assertEquals(2, entry.get("memberOf").size());
        Set<String> expectedMemberOf = Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org");
        Set<String> foundMemberOf = new HashSet<>(2);
        Iterator<Value> memberOfs = entry.get("memberOf").iterator();
        while (memberOfs.hasNext()) {
            foundMemberOf.add(memberOfs.next().getString());
        }
        assertEquals(expectedMemberOf, foundMemberOf);
    }

    @Test
    public void testGetUserBySAMAccountName() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "sAMAccountName");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("TestSam1", schemaManager);
        assertEquals("TestSam1", entry.get("uid").getString());
        assertEquals("TestCn1", entry.get("cn").getString());
        assertEquals("ldaptest1@example.com", entry.get("mail").getString());
        assertEquals("Test user ldaptest1", entry.get("description").getString());
        assertEquals("TestSam1", entry.get("sAMAccountName").getString());
        assertEquals(2, entry.get("memberOf").size());
        Set<String> expectedMemberOf = Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org");
        Set<String> foundMemberOf = new HashSet<>(2);
        Iterator<Value> memberOfs = entry.get("memberOf").iterator();
        while (memberOfs.hasNext()) {
            foundMemberOf.add(memberOfs.next().getString());
        }
        assertEquals(expectedMemberOf, foundMemberOf);
    }

    @Test
    public void testGetUserUseMemberOf() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("ldaptest2", schemaManager);
        assertEquals("ldaptest2", entry.get("uid").getString());
        assertEquals("TestCn2", entry.get("cn").getString());
        assertEquals("ldaptest2@example.com", entry.get("mail").getString());
        assertEquals("Test user ldaptest2", entry.get("description").getString());
        assertNull(entry.get("sAMAccountName"));
        assertEquals(2, entry.get("memberOf").size());
        Set<String> expectedMemberOf = Set.of(
                "cn=groupMemberOf1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=groupMemberOf2,ou=groups,dc=hadoop,dc=apache,dc=org");
        Set<String> foundMemberOf = new HashSet<>(2);
        Iterator<Value> memberOfs = entry.get("memberOf").iterator();
        while (memberOfs.hasNext()) {
            foundMemberOf.add(memberOfs.next().getString());
        }
        assertEquals(expectedMemberOf, foundMemberOf);

    }

    @Test
    public void testGetUserGroups() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1");
        assertTrue(userGroups.contains("group1"));
        assertTrue(userGroups.contains("group2"));
    }

    @Test
    public void testGetUserGroupsNoGroups() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest2");
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsNoUser() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("nobody");
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsUseMemberOf() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest2");
        assertTrue(userGroups.contains("groupMemberOf1"));
        assertTrue(userGroups.contains("groupMemberOf2"));
    }

    @Test
    public void testGetUserGroupsUseMemberOfNoGroups() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1");
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsUseMemberOfNoUser() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("nobody");
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testSearchUsers() throws Exception {
        // searches by uid by default
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<Entry> entries = ldapProxyBackend.searchUsers("*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(3, foundUids.size());
        assertTrue(foundUids.contains("ldaptest1"));
        assertTrue(foundUids.contains("ldaptest2"));
        assertTrue(foundUids.contains("guest"));
    }

    @Test
    public void testSearchUsersPartial() throws Exception {
        // searches by uid by default
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<Entry> entries = ldapProxyBackend.searchUsers("ldap*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(2, foundUids.size());
        assertTrue(foundUids.contains("ldaptest1"));
        assertTrue(foundUids.contains("ldaptest2"));
    }

    @Test
    public void testSearchUsersNoneFound() throws Exception {
        // searches by uid by default
        ldapProxyBackend.initialize(ldapBackendConfig);

        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersByCn() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "cn");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(3, foundUids.size());
        assertTrue(foundUids.contains("TestCn1"));
        assertTrue(foundUids.contains("TestCn2"));
        assertTrue(foundUids.contains("Guest"));
    }

    @Test
    public void testSearchUsersPartialByCn() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "cn");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("TestCn*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(2, foundUids.size());
        assertTrue(foundUids.contains("TestCn1"));
        assertTrue(foundUids.contains("TestCn2"));
    }

    @Test
    public void testSearchUsersNoneFoundByCn() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "cn");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersBySAMAccountName() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "sAMAccountName");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(2, foundUids.size());
        assertTrue(foundUids.contains("TestSam1"));
        assertTrue(foundUids.contains("TestSam2"));
    }

    @Test
    public void testSearchUsersPartialBySAMAccountName() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "sAMAccountName");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("TestSam*", schemaManager);
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        assertEquals(2, foundUids.size());
        assertTrue(foundUids.contains("TestSam1"));
        assertTrue(foundUids.contains("TestSam2"));
    }

    @Test
    public void testSearchUsersNoneFoundBySAMAccountName() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", "sAMAccountName");
        ldapProxyBackend.initialize(config);

        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }
}