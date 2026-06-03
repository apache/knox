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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

        // load test data from ldif files
        CoreSession session = directoryService.getAdminSession();
        loadLdif(session, "/ldap-proxy-backend-test.ldif");
        loadLdif(session, "/ldap-recursive-test.ldif");

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

    private static void loadLdif(CoreSession session, String ldifResource) throws Exception {
        File ldifFile = new File(LdapProxyBackendTest.class.getResource(ldifResource).toURI());
        LdifFileLoader lfl = new LdifFileLoader(session, ldifFile, null);
        lfl.execute();
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
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserNotFound() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);

        Entry entry = ldapProxyBackend.getUser("nouser", schemaManager);
        assertNull(entry);
    }

    @Test
    public void testGetUserByUID() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("uid");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserByCN() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("TestCn1", schemaManager);
        validateUserEntry(entry, "TestCn1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("TestSam1", schemaManager);
        validateUserEntry(entry, "TestSam1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        assertEquals("TestSam1", entry.get("sAMAccountName").getString());
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserUseMemberOf() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("ldaptest2", schemaManager);
        validateUserEntry(entry, "ldaptest2", "TestCn2", "ldaptest2@example.com", "Test user ldaptest2");
        validateMemberOf(entry, Set.of(
                "cn=groupMemberOf1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=groupMemberOf2,ou=groups,dc=hadoop,dc=apache,dc=org"));
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
        ldapProxyBackend.initialize(ldapBackendConfig);
        validateUserSearch("*", 3, Set.of("ldaptest1", "ldaptest2", "guest"));
    }

    @Test
    public void testSearchUsersPartial() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);
        validateUserSearch("ldap*", 2, Set.of("ldaptest1", "ldaptest2"));
    }

    @Test
    public void testSearchUsersNoneFound() throws Exception {
        ldapProxyBackend.initialize(ldapBackendConfig);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend.initialize(config);
        validateUserSearch("*", 3, Set.of("TestCn1", "TestCn2", "Guest"));
    }

    @Test
    public void testSearchUsersPartialByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend.initialize(config);
        validateUserSearch("TestCn*", 2, Set.of("TestCn1", "TestCn2"));
    }

    @Test
    public void testSearchUsersNoneFoundByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend.initialize(config);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend.initialize(config);
        validateUserSearch("*", 2, Set.of("TestSam1", "TestSam2"));
    }

    @Test
    public void testSearchUsersPartialBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend.initialize(config);
        validateUserSearch("TestSam*", 2, Set.of("TestSam1", "TestSam2"));
    }

    @Test
    public void testSearchUsersNoneFoundBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend.initialize(config);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testGetRecursiveUserGroupsDepth2() throws Exception {
        Map<String, String> config = createRecursiveConfig(2);
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser");
        assertEquals(4, userGroups.size());
        assertTrue(userGroups.contains("level1Group"));
        assertTrue(userGroups.contains("level2Group"));
        assertTrue(userGroups.contains("cycleGroupA"));
        assertTrue(userGroups.contains("cycleGroupB"));
    }

    @Test
    public void testGetRecursiveUserGroupsDepth4() throws Exception {
        Map<String, String> config = createRecursiveConfig(4);
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser");
        assertEquals(6, userGroups.size());
        assertTrue(userGroups.contains("level1Group"));
        assertTrue(userGroups.contains("level2Group"));
        assertTrue(userGroups.contains("level3Group"));
        assertTrue(userGroups.contains("level4Group"));
        assertTrue(userGroups.contains("cycleGroupA"));
        assertTrue(userGroups.contains("cycleGroupB"));
    }

    @Test
    public void testGetRecursiveUserGroupsWithCycle() throws Exception {
        Map<String, String> config = createRecursiveConfig(10);
        ldapProxyBackend.initialize(config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser");
        assertTrue(userGroups.contains("cycleGroupA"));
        assertTrue(userGroups.contains("cycleGroupB"));
    }

    @Test
    public void testGetUserRecursiveGroups() throws Exception {
        Map<String, String> config = createRecursiveConfig(5);
        ldapProxyBackend.initialize(config);

        Entry entry = ldapProxyBackend.getUser("recursiveUser", schemaManager);
        validateMemberOf(entry, Set.of(
                "cn=level1Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level2Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level3Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level4Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=cycleGroupA,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=cycleGroupB,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testSearchUsersRecursiveWithSharedGroups() throws Exception {
        Map<String, String> config = createRecursiveConfig(5);

        final AtomicInteger cacheHits = new AtomicInteger(0);
        ldapProxyBackend = new LdapProxyBackend() {
            @Override
            protected Map<String, Set<Entry>> createResolvedParentsCache() {
                return new HashMap<>() {
                    @Override
                    public Set< org.apache.directory.api.ldap.model.entry.Entry> get(Object key) {
                        if (super.get(key) != null) {
                            cacheHits.incrementAndGet();
                        }
                        return super.get(key);
                    }
                };
            }
        };
        ldapProxyBackend.initialize(config);

        // Search for all recursive users (recursiveUser and recursiveUser2)
        // They share level1Group, cycleGroupA, and all their ancestors.
        List<Entry> entries = ldapProxyBackend.searchUsers("recursiveUser*", schemaManager);
        assertEquals(2, entries.size());

        Set<String> expectedGroups = Set.of(
                "cn=level1Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level2Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level3Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=level4Group,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=cycleGroupA,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org",
                "cn=cycleGroupB,ou=recursiveGroups,dc=hadoop,dc=apache,dc=org");

        for (Entry entry : entries) {
            validateMemberOf(entry, expectedGroups);
        }

        // Verify that caching actually happened.
        // For the second user, many groups should have been found in the cache.
        assertEquals("Expected 6 cache hits for shared groups, but got " + cacheHits.get(), 6, cacheHits.get());
    }

    // Helper methods for refactoring

    private Map<String, String> createConfigWithUserAttr(String attr) {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userIdentifierAttribute", attr);
        return config;
    }

    private Map<String, String> createRecursiveConfig(int depth) {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("recursiveGroupResolution", "true");
        config.put("recursiveGroupResolutionMaxDepth", String.valueOf(depth));
        config.put("userSearchBase", "ou=recursivePeople,dc=hadoop,dc=apache,dc=org");
        config.put("groupSearchBase", "ou=recursiveGroups,dc=hadoop,dc=apache,dc=org");
        return config;
    }

    private void validateUserEntry(Entry entry, String expectedUid, String expectedCn, String expectedMail, String expectedDesc) throws Exception {
        assertEquals(expectedUid, entry.get("uid").getString());
        assertEquals(expectedCn, entry.get("cn").getString());
        assertEquals(expectedMail, entry.get("mail").getString());
        assertEquals(expectedDesc, entry.get("description").getString());
    }

    private void validateMemberOf(Entry entry, Set<String> expectedGroups) throws Exception {
        assertEquals(expectedGroups.size(), entry.get("memberOf").size());
        Set<String> foundGroups = new HashSet<>();
        for (Value value : entry.get("memberOf")) {
            foundGroups.add(value.getString());
        }
        assertEquals(expectedGroups, foundGroups);
    }

    private void validateUserSearch(String filter, int expectedSize, Set<String> expectedUids) throws Exception {
        List<Entry> entries = ldapProxyBackend.searchUsers(filter, schemaManager);
        assertEquals(expectedSize, entries.size());
        Set<String> foundUids = new HashSet<>();
        for (Entry entry : entries) {
            foundUids.add(entry.get("uid").getString());
        }
        for (String uid : expectedUids) {
            assertTrue("Expected UID " + uid + " not found", foundUids.contains(uid));
        }
    }
}
