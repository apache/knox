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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapOperationException;
import org.apache.directory.api.ldap.model.message.BindRequest;
import org.apache.directory.api.ldap.model.message.LdapResult;
import org.apache.directory.api.ldap.model.message.ResultCodeEnum;
import org.apache.directory.api.ldap.model.message.SearchRequest;
import org.apache.directory.api.ldap.model.message.SearchResultDone;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.factory.JdbmPartitionFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.ldap.LdapSession;
import org.apache.directory.server.ldap.handlers.LdapRequestHandler;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.knox.gateway.security.ldap.SimpleDirectoryService;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LdapProxyBackendTest {
    private static final int PAGE_SIZE = 2;
    private static final String[] ALL_ATTRIBUTES = {"*"};

    private static Map<String, String> ldapBackendConfig;

    private static TcpTransport transport;
    private static DirectoryService directoryService;
    private static LdapServer ldapServer;
    private static SchemaManager schemaManager;
    private static CapturingSearchRequestHandler capturingSearchRequestHandler;
    private static CapturingBindRequestHandler capturingBindRequestHandler;

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

        capturingSearchRequestHandler = new CapturingSearchRequestHandler(ldapServer.getSearchRequestHandler());
        ldapServer.setSearchHandlers(
                capturingSearchRequestHandler,
                ldapServer.getSearchResultEntryHandler(),
                ldapServer.getSearchResultReferenceHandler(),
                ldapServer.getSearchResultDoneHandler());

        capturingBindRequestHandler = new CapturingBindRequestHandler(ldapServer.getBindRequestHandler());
        ldapServer.setBindHandlers(
                capturingBindRequestHandler,
                ldapServer.getBindResponseHandler());

        // Setup common backend config values for tests
        ldapBackendConfig = Map.of(
                "baseDn", "dc=proxy,dc=com",
                "remoteBaseDn", "dc=hadoop,dc=apache,dc=org",
                "url", "ldap://localhost:" + transport.getAcceptor().getLocalAddress().getPort(),
                "systemUsername", "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org",
                "systemPassword", "guest-password",
                "userSearchBase", "ou=people,dc=hadoop,dc=apache,dc=org",
                "groupSearchBase", "ou=groups,dc=hadoop,dc=apache,dc=org",
                "dnMappingEnabled", "true");
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

    @After
    public void tearDown() throws Exception {
        capturingSearchRequestHandler.reset();
        capturingBindRequestHandler.reset();
        if (ldapProxyBackend != null) {
            ldapProxyBackend.close();
        }
    }

    @Test
    public void testGetUserByDefaultUserSearchFilter() throws Exception {
        // default searches by uid and uses group search for membership
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=proxy,dc=com",
                "cn=group2,ou=groups,dc=proxy,dc=com",
                "cn=group3,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testGetUserByDefaultUserSearchFilterNoDnMapping() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("dnMappingEnabled", "false");
        // default searches by uid and uses group search for membership
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group3,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserNotFound() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        Entry entry = ldapProxyBackend.getUser("nouser", schemaManager);
        assertNull(entry);
    }

    @Test
    public void testGetUserByUID() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("uid");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=proxy,dc=com",
                "cn=group2,ou=groups,dc=proxy,dc=com",
                "cn=group3,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testGetUserByCN() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("TestCn1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=proxy,dc=com",
                "cn=group2,ou=groups,dc=proxy,dc=com",
                "cn=group3,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testGetUserByCNFillsInUIDIfNotPresent() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("TestCn3", schemaManager);
        validateUserEntry(entry, "TestCn3", "TestCn3", "ldaptest3@example.com", "Test user ldaptest3");
    }

    @Test
    public void testGetUserBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("TestSam1", schemaManager);
        validateUserEntry(entry, "ldaptest1", "TestCn1", "ldaptest1@example.com", "Test user ldaptest1");
        assertEquals("TestSam1", entry.get("sAMAccountName").getString());
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=proxy,dc=com",
                "cn=group2,ou=groups,dc=proxy,dc=com",
                "cn=group3,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testGetUserBySAMAccountNameFillsInUIDIfNotPresent() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("TestSam3", schemaManager);
        validateUserEntry(entry, "TestSam3", "TestCn3", "ldaptest3@example.com", "Test user ldaptest3");
        assertEquals("TestSam3", entry.get("sAMAccountName").getString());
    }

    @Test
    public void testGetUserUseMemberOf() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("ldapmemberof", schemaManager);
        validateUserEntry(entry, "ldapmemberof", "TestMemberOf", "ldapmemberof@example.com", "Test user ldapmemberof");
        validateMemberOf(entry, Set.of(
                "cn=groupMemberOf1,ou=groups,dc=proxy,dc=com",
                "cn=groupMemberOf2,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testGetUserGroups() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1", schemaManager);
        assertTrue(userGroups.contains("group1"));
        assertTrue(userGroups.contains("group2"));
        assertTrue(userGroups.contains("group3"));
    }

    @Test
    public void testGetUserGroupsPaging() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1", schemaManager);
        assertEquals(3, userGroups.size());
        assertEquals(2, findSearchRequests("ou=groups,dc=hadoop,dc=apache,dc=org", "ldaptest1").size());
    }

    @Test
    public void testGetUserGroupsPagingExceedsMaxResultSetSize() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        config.put("maxResultSetSize", "1");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1", schemaManager);
        assertEquals(1, userGroups.size()); // results are truncated to maxResultSetSize
        assertEquals(1, findSearchRequests("ou=groups,dc=hadoop,dc=apache,dc=org", "ldaptest1").size());
    }

    @Test
    public void testGetUserGroupsNoGroups() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest2", schemaManager);
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsNoUser() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        List<String> userGroups = ldapProxyBackend.getUserGroups("nobody", schemaManager);
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsUseMemberOf() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldapmemberof", schemaManager);
        assertTrue(userGroups.contains("groupMemberOf1"));
        assertTrue(userGroups.contains("groupMemberOf2"));
    }

    @Test
    public void testGetUserGroupsUseMemberOfNoGroups() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("ldaptest1", schemaManager);
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsUseMemberOfNoUser() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("nobody", schemaManager);
        assertTrue(userGroups.isEmpty());
    }

    @Test
    public void testGetUserGroupsUseMemberOfRecursive() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(10);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("memberOfUser", schemaManager);
        assertTrue(userGroups.contains("memberOflevel1"));
        assertTrue(userGroups.contains("memberOflevel2"));
        assertTrue(userGroups.contains("memberOflevel3"));
        assertTrue(userGroups.contains("memberOflevel4"));
        assertTrue(userGroups.contains("memberOfCycleA"));
        assertTrue(userGroups.contains("memberOfCycleB"));
    }

    @Test
    public void testGetUserGroupsUseMemberOfRecursiveDepth2() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(2);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("memberOfUser", schemaManager);
        assertTrue(userGroups.contains("memberOflevel1"));
        assertTrue(userGroups.contains("memberOflevel2"));
        assertFalse("Level 3 should not appear at max depth 2", userGroups.contains("memberOflevel3"));
    }

    @Test
    public void testSearchUsers() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateUserSearch("*", 4, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
    }

    @Test
    public void testSearchUsersWithPaging() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateUserSearch("*", 4, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
        assertEquals(2, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=*").size());
    }

    @Test
    public void testSearchUsersWithPagingExceedsMaxResultSetSize() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        config.put("maxResultSetSize", "1");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.searchUsers("*", schemaManager);
        assertEquals(1, entries.size()); // only expect 1 page of results, truncated to maxResultSetSize
        assertEquals(1, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=*").size());
    }

    @Test
    public void testSearchUsersPartial() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateUserSearch("ldap*", 3, Set.of("ldaptest1", "ldaptest2", "ldapmemberof"));
    }

    @Test
    public void testSearchUsersNoneFound() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateUserSearch("*", 5, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "Guest", "TestCn3"));
    }

    @Test
    public void testSearchUsersPartialByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateUserSearch("TestCn*", 3, Set.of("ldaptest1", "ldaptest2", "TestCn3"));
    }

    @Test
    public void testSearchUsersNoneFoundByCn() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("cn");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchUsersBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateUserSearch("*", 4, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "TestSam3"));
    }

    @Test
    public void testSearchUsersPartialBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateUserSearch("TestSam*", 3, Set.of("ldaptest1", "ldaptest2", "TestSam3"));
    }

    @Test
    public void testSearchUsersNoneFoundBySAMAccountName() throws Exception {
        Map<String, String> config = createConfigWithUserAttr("sAMAccountName");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        List<Entry> entries = ldapProxyBackend.searchUsers("nobody*", schemaManager);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testGetRecursiveUserGroupsDepth2() throws Exception {
        Map<String, String> config = createRecursiveConfig(2);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser", schemaManager);
        assertEquals(4, userGroups.size());
        assertTrue(userGroups.contains("level1Group"));
        assertTrue(userGroups.contains("level2Group"));
        assertTrue(userGroups.contains("cycleGroupA"));
        assertTrue(userGroups.contains("cycleGroupB"));
    }

    @Test
    public void testGetRecursiveUserGroupsDepth4() throws Exception {
        Map<String, String> config = createRecursiveConfig(4);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser", schemaManager);
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
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<String> userGroups = ldapProxyBackend.getUserGroups("recursiveUser", schemaManager);
        assertTrue(userGroups.contains("cycleGroupA"));
        assertTrue(userGroups.contains("cycleGroupB"));
    }

    @Test
    public void testGetUserRecursiveGroups() throws Exception {
        Map<String, String> config = createRecursiveConfig(5);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        Entry entry = ldapProxyBackend.getUser("recursiveUser", schemaManager);
        validateMemberOf(entry, Set.of(
                "cn=level1Group,ou=groups,dc=proxy,dc=com",
                "cn=level2Group,ou=groups,dc=proxy,dc=com",
                "cn=level3Group,ou=groups,dc=proxy,dc=com",
                "cn=level4Group,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupA,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchUsersRecursiveWithSharedGroups() throws Exception {
        testSearchUsersRecursiveWithSharedGroups(
                () -> ldapProxyBackend.searchUsers("recursiveUser*", schemaManager));
    }

    @Test
    public void testSearchRecursiveWithSharedGroups() throws Exception {
        testSearchUsersRecursiveWithSharedGroups(
                () -> ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser*)", schemaManager, ALL_ATTRIBUTES));
    }

    @Test
    public void testSearchObjectClassInetOrgPerson() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=people,dc=proxy,dc=com", "(objectClass=inetOrgPerson)", 5, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest", "TestCn3"));
    }

    @Test
    public void testSearchByUid() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=people,dc=proxy,dc=com", "(uid=guest)", 1, Set.of("guest"));
    }

    @Test
    public void testSearchByUidWildcard() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=people,dc=proxy,dc=com", "(uid=*)", 4, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
    }

    @Test
    public void testSearchWithPaging() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateSearch("ou=people,dc=proxy,dc=com", "(uid=*)", 4, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
        assertEquals(2, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=*").size());
    }

    @Test
    public void testSearchWithPagingExceedsMaxResultSize() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        config.put("maxResultSetSize", "1");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=*)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size()); // only expect 1 page truncated to maxResultSetSize
        assertEquals(1, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=*").size());
    }

    @Test
    public void testSearchSplitsFilterWithPagingExceedsMaxResultSize() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("maxFilterLength", "40");
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        config.put("maxResultSetSize", "3");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE,
                "(|(uid=ldaptest1)(uid=ldaptest2)(uid=ldapmemberof)(uid=guest)(uid=ldaptest1))", schemaManager, ALL_ATTRIBUTES);
        // expect results truncated to maxResultSetSize
        assertEquals(3, entries.size());
        // expect 2 pages
        assertEquals(2, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "").size());
    }

    @Test
    public void testSearchSplitsFilterExceedingMaxFilterLength() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("maxFilterLength", "40");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateSearch("ou=people,dc=proxy,dc=com",
                "(|(uid=ldaptest1)(uid=ldaptest2)(uid=ldapmemberof)(uid=guest)(uid=ldaptest1))", 4,
                Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
        // the filter was spread over several queries and no single query carried every uid
        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=");
        assertTrue("Expected more than one search request but found " + requests.size(), requests.size() > 1);
        requests.forEach(request -> assertTrue(request.getFilter().toString(), !request.getFilter().toString().contains("uid=guest")
                || !request.getFilter().toString().contains("uid=ldaptest2")));
    }

    @Test
    public void testSearchDoesNotSplitFilterWhenMaxFilterLengthDisabled() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("maxFilterLength", "0");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        validateSearch("ou=people,dc=proxy,dc=com",
                "(|(uid=ldaptest1)(uid=ldaptest2)(uid=ldapmemberof)(uid=guest))", 4,
                Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "guest"));
        assertEquals(1, findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=ldaptest1").size());
    }

    @Test
    public void testSearchByUidSubstringWildcard() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=people,dc=proxy,dc=com", "(uid=ldap*)", 3, Set.of("ldaptest1", "ldaptest2", "ldapmemberof"));
    }

    @Test
    public void testSearchObjectClassGroupOfNames() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=groups,dc=proxy,dc=com", "(objectClass=groupOfNames)", 4, Set.of("group1", "group2", "group3", "nameddifferently"));
    }

    @Test
    public void testSearchByCn() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=groups,dc=proxy,dc=com", "(cn=group1)", 1, Set.of("group1"));
    }

    @Test
    public void testSearchByCnWildcard() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=groups,dc=proxy,dc=com", "(cn=*)", 4, Set.of("group1", "group2", "group3", "nameddifferently"));
    }

    @Test
    public void testSearchByCnWSubstringildcard() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("ou=groups,dc=proxy,dc=com", "(cn=group*)", 3, Set.of("group1", "group2", "group3"));
    }

    @Test
    public void testSearchByUidOrCnWildcard() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        validateSearch("dc=proxy,dc=com", "(|(uid=ldap*)(cn=group*))", 6, Set.of("ldaptest1", "ldaptest2", "ldapmemberof", "group1", "group2", "group3"));
    }

    @Test
    public void testSearchRequestsMemberOfAttributeWhenUseMemberOfEnabled() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=ldaptest1)", schemaManager, ALL_ATTRIBUTES);

        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "ldaptest1");
        assertEquals("Expect exactly 1 matching request found", 1, requests.size());
        assertTrue("memberOf must be explicitly requested when useMemberOf is enabled",
                requests.get(0).getAttributes().contains("memberOf"));
    }

    @Test
    public void testSearchDoesNotRequestMemberOfAttributeWhenUseMemberOfDisabled() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=ldaptest1)", schemaManager, ALL_ATTRIBUTES);

        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "ldaptest1");
        assertEquals("Expect exactly 1 matching request found", 1, requests.size());
        assertFalse("memberOf should not be explicitly requested when useMemberOf is disabled",
                requests.get(0).getAttributes().contains("memberOf"));
    }

    @Test
    public void testSearchMemberOfFilterMappedToMembersWhenUseMemberOfDisabled() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        validateSearch("ou=people,dc=proxy,dc=com", "(memberOf=cn=group1,ou=groups,dc=proxy,dc=com)",
                1, Set.of("ldaptest1"));

        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "uid=ldaptest1");
        assertEquals("Expect exactly 1 matching request found", 1, requests.size());
        assertFalse("memberOf must not be sent to a backend that does not support it",
                requests.get(0).getFilter().toString().toLowerCase(Locale.ROOT).contains("memberof"));
    }

    @Test
    public void testSearchMemberOfFilterCombinedWithOtherClausesWhenUseMemberOfDisabled() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        validateSearch("ou=people,dc=proxy,dc=com",
                "(|(memberOf=cn=nameddifferently,ou=groups,dc=proxy,dc=com)(uid=ldaptest2))",
                2, Set.of("guest", "ldaptest2"));
    }

    @Test
    public void testSearchMemberOfFilterForUnknownGroupLeftUnchangedWhenUseMemberOfDisabled() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);

        validateSearch("ou=people,dc=proxy,dc=com", "(memberOf=cn=nosuchgroup,ou=groups,dc=proxy,dc=com)",
                0, Set.of());

        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "memberOf");
        assertEquals("Filter should be passed to the backend unchanged", 1, requests.size());
    }

    @Test
    public void testSearchMemberOfFilterNotMappedWhenUseMemberOfEnabled() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE,
                "(memberOf=cn=group1,ou=groups,dc=proxy,dc=com)", schemaManager, ALL_ATTRIBUTES);

        List<SearchRequest> requests = findSearchRequests("ou=people,dc=hadoop,dc=apache,dc=org", "memberOf");
        assertEquals("memberOf filter should be passed to the backend as is", 1, requests.size());
        assertTrue(capturingSearchRequestHandler.getRequests().stream()
                .noneMatch(r -> r.getScope() == SearchScope.OBJECT && r.getBase().getName().startsWith("cn=group1")));
    }

    @Test
    public void testSearchOmitsGroupMembershipsWhenMemberOfNotRequested() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", createRecursiveConfig(2));
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, new String[] {"uid", "cn"});
        assertEquals(1, entries.size());
        assertNull(entries.get(0).get("memberOf"));

        entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, null);
        assertEquals(1, entries.size());
        assertNull(entries.get(0).get("memberOf"));
    }

    @Test
    public void testSearchAddsGroupMembershipsWhenMemberOfRequestedByOid() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", createRecursiveConfig(2));
        String memberOfOid = schemaManager.getAttributeType("memberOf").getOid();
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, new String[] {memberOfOid});
        assertEquals(1, entries.size());
        assertNotNull(entries.get(0).get("memberOf"));
    }

    @Test
    public void testSearchAddsGroupMembershipsWhenAllAttributesRequested() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", createRecursiveConfig(2));
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, new String[] {"*"});
        assertEquals(1, entries.size());
        assertNotNull(entries.get(0).get("memberOf"));
    }

    @Test
    public void testSearchAddsGroupMembershipsWhenMemberOfRequestedCaseInsensitive() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", createRecursiveConfig(2));
        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, new String[] {"uid", "MEMBEROF"});
        assertEquals(1, entries.size());
        assertNotNull(entries.get(0).get("memberOf"));
    }

    @Test
    public void testSearchRecursiveUserGroupsDepth2() throws Exception {
        Map<String, String> config = createRecursiveConfig(2);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=level1Group,ou=groups,dc=proxy,dc=com",
                "cn=level2Group,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupA,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveGroupsDepth4() throws Exception {
        Map<String, String> config = createRecursiveConfig(4);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=level1Group,ou=groups,dc=proxy,dc=com",
                "cn=level2Group,ou=groups,dc=proxy,dc=com",
                "cn=level3Group,ou=groups,dc=proxy,dc=com",
                "cn=level4Group,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupA,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveGroups() throws Exception {
        Map<String, String> config = createRecursiveConfig(10);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=recursiveUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=level1Group,ou=groups,dc=proxy,dc=com",
                "cn=level2Group,ou=groups,dc=proxy,dc=com",
                "cn=level3Group,ou=groups,dc=proxy,dc=com",
                "cn=level4Group,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupA,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveUserGroupsViaMemberOfDepth2() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(2);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=memberOfUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=memberOflevel1,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel2,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveGroupsViaMemberOfDepth3() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(3);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=memberOfUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=memberOflevel1,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel2,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel3,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleA,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveGroupsViaMemberOfDepth4() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(4);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=memberOfUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=memberOflevel1,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel2,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel3,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel4,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleA,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchRecursiveGroupsViaMemberOf() throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(10);
        config.put("useMemberOf", "true");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        List<Entry> entries = ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=memberOfUser)", schemaManager, ALL_ATTRIBUTES);
        assertEquals(1, entries.size());
        validateMemberOf(entries.get(0), Set.of(
                "cn=memberOflevel1,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel2,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel3,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel4,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleA,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleB,ou=groups,dc=proxy,dc=com"));
    }

    @Test
    public void testSearchUsersRecursiveWithSharedGroupsViaMemberOf() throws Exception {
        testSearchUsersRecursiveSharedGroupsViaMemberOf(
                () -> ldapProxyBackend.searchUsers("memberOfUser*", schemaManager));
    }

    @Test
    public void testSearchRecursiveWithSharedGroupsViaMemberOf() throws Exception {
        testSearchUsersRecursiveSharedGroupsViaMemberOf(
                () -> ldapProxyBackend.search("ou=people,dc=proxy,dc=com", SearchScope.SUBTREE, "(uid=memberOfUser*)", schemaManager, ALL_ATTRIBUTES));
    }

    @Test
    public void testAuthenticate() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
        assertTrue(ldapProxyBackend.authenticate(dn, "guest-password"));
        assertEquals(1, capturingBindRequestHandler.getRequests().size());
        BindRequest bindRequest = capturingBindRequestHandler.getRequests().get(0);
        assertEquals("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org", bindRequest.getDn().getName());
        assertEquals("guest-password", new String(bindRequest.getCredentials(), StandardCharsets.UTF_8));
    }

    @Test
    public void testAuthenticateBadPassword() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
        assertFalse(ldapProxyBackend.authenticate(dn, "bad-password"));
        assertEquals(1, capturingBindRequestHandler.getRequests().size());
    }

    @Test
    public void testAuthenticateNoPassword() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
        assertFalse(ldapProxyBackend.authenticate(dn, ""));
        // No bind request should be sent to server if no password is provided
        assertEquals(0, capturingBindRequestHandler.getRequests().size());
    }

    @Test
    public void testAuthenticateBadUser() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("uid=nobody,ou=people,dc=hadoop,dc=apache,dc=org");
        assertFalse(ldapProxyBackend.authenticate(dn, "guest-password"));
        assertEquals(1, capturingBindRequestHandler.getRequests().size());
    }

    @Test
    public void testAuthenticateNoUser() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("");
        assertFalse(ldapProxyBackend.authenticate(dn, "guest-password"));
        // No bind request should be sent to server if no user is provided
        assertEquals(0, capturingBindRequestHandler.getRequests().size());
    }

    @Test
    public void testAuthenticateConvertsBaseDn() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("testbackend", ldapBackendConfig);
        Dn dn = new Dn("uid=guest,ou=people,dc=proxy,dc=com");
        assertTrue(ldapProxyBackend.authenticate(dn, "guest-password"));
    }

    @Test
    public void testAuthenticateConvertsUserSearchBase() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("userSearchBase", "ou=recursiveMemberOfPeople,dc=hadoop,dc=apache,dc=org");
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);
        Dn dn = new Dn("uid=memberOfUser2,ou=people,dc=proxy,dc=com");
        assertTrue(ldapProxyBackend.authenticate(dn, "memberOfUser2-password"));
    }

    @Test
    public void testIsSupportedSearchBase() throws Exception {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        // Test searching remote dn
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=people,dc=hadoop,dc=apache,dc=org"));
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=people,dc=hadoop,DC=Apache,DC=Org")); // mixed case
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=groups,dc=hadoop,dc=apache,dc=org"));
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=groups,dc=hadoop,DC=Apache,DC=Org")); // mixed case

        // Test searching proxy dn
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=people,dc=proxy,dc=com"));
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=people,DC=Proxy,DC=Com")); // mixed case
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=groups,dc=proxy,dc=com"));
        assertTrue(ldapProxyBackend.isSupportedSearchBase("ou=groups,DC=Proxy,DC=Com")); // mixed case

        // Test searching arbitrary dn
        assertFalse(ldapProxyBackend.isSupportedSearchBase("cn=schema"));
        assertFalse(ldapProxyBackend.isSupportedSearchBase("cn=config"));
        assertFalse(ldapProxyBackend.isSupportedSearchBase(null));
        assertFalse(ldapProxyBackend.isSupportedSearchBase(""));
        assertFalse(ldapProxyBackend.isSupportedSearchBase("dc=other,dc=base,dc=org"));
    }

    @Test
    public void testPerformPagedSearchThrowsOnFailure() throws Exception{
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        // Mock out unsuccessful paging result
        LdapResult mockResult = createMock(LdapResult.class);
        expect(mockResult.getResultCode()).andReturn(ResultCodeEnum.UNWILLING_TO_PERFORM).atLeastOnce();
        expect(mockResult.getDiagnosticMessage()).andReturn("test error message");
        SearchResultDone mockDone = createMock(SearchResultDone.class);
        expect(mockDone.getLdapResult()).andReturn(mockResult);
        SearchCursor mockCursor = createMock(SearchCursor.class);
        expect(mockCursor.next()).andReturn(false);
        expect(mockCursor.isDone()).andReturn(true);
        expect(mockCursor.getSearchResultDone()).andReturn(mockDone);
        LdapConnection mockConnection = createMock(LdapConnection.class);
        expect(mockConnection.search(anyObject(SearchRequest.class))).andReturn(mockCursor);
        replay(mockResult,
                mockDone,
                mockCursor,
                mockConnection);

        LdapOperationException exception = assertThrows(
                LdapOperationException.class,
                () -> ldapProxyBackend.performPagedSearch(mockConnection, "ou=people,dc=proxy,dc=com", "(objectclass=inetOrgPerson)", SearchScope.SUBTREE, "*"));
        assertEquals(ResultCodeEnum.UNWILLING_TO_PERFORM, exception.getResultCode());
        assertEquals("test error message", exception.getMessage());
    }

    @Test
    public void testPerformPagedSearchThrowsOnNullSearchResultDone() throws Exception{
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("pageSize", Integer.toString(PAGE_SIZE));
        ldapProxyBackend = new LdapProxyBackend("testbackend", config);

        // Mock out unsuccessful paging result
        LdapResult mockResult = createMock(LdapResult.class);
        expect(mockResult.getResultCode()).andReturn(ResultCodeEnum.UNWILLING_TO_PERFORM).atLeastOnce();
        expect(mockResult.getDiagnosticMessage()).andReturn("test error message");
        SearchCursor mockCursor = createMock(SearchCursor.class);
        expect(mockCursor.next()).andReturn(false);
        expect(mockCursor.isDone()).andReturn(true);
        expect(mockCursor.getSearchResultDone()).andReturn(null);
        LdapConnection mockConnection = createMock(LdapConnection.class);
        expect(mockConnection.search(anyObject(SearchRequest.class))).andReturn(mockCursor);
        replay(mockResult,
                mockCursor,
                mockConnection);

        LdapException exception = assertThrows(
                LdapException.class,
                () -> ldapProxyBackend.performPagedSearch(mockConnection, "ou=people,dc=proxy,dc=com", "(objectclass=inetOrgPerson)", SearchScope.SUBTREE, "*"));
        assertEquals("The LDAP search operation failed to complete fully.", exception.getMessage());
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

    private Map<String, String> createRecursiveConfigForMemberOf(int depth) {
        Map<String, String> config = new HashMap<>(ldapBackendConfig);
        config.put("recursiveGroupResolution", "true");
        config.put("recursiveGroupResolutionMaxDepth", String.valueOf(depth));
        config.put("userSearchBase", "ou=recursiveMemberOfPeople,dc=hadoop,dc=apache,dc=org");
        config.put("groupSearchBase", "ou=recursiveMemberOfGroups,dc=hadoop,dc=apache,dc=org");
        config.put("useMemberOf", "true");
        return config;
    }

    private void validateUserEntry(Entry entry, String expectedUid, String expectedCn, String expectedMail, String expectedDesc) throws Exception {
        assertEquals(expectedUid, entry.get("uid").getString());
        assertEquals(expectedCn, entry.get("cn").getString());
        assertEquals(expectedMail, entry.get("mail").getString());
        assertEquals(expectedDesc, entry.get("description").getString());
    }

    private void validateMemberOf(Entry entry, Set<String> expectedGroups) throws Exception {
        assertNotNull(entry.get("memberOf"));
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

    private void validateSearch(String searchBase, String filter, int expectedSize, Set<String> expectedRdns) throws Exception {
        List<Entry> entries = ldapProxyBackend.search(searchBase, SearchScope.SUBTREE, filter, schemaManager, ALL_ATTRIBUTES);
        Set<String> foundRdns = new HashSet<>();
        for (Entry entry : entries) {
            Dn dn = entry.getDn();
            foundRdns.add(dn.getRdn().getValue());
        }
        assertEquals(expectedSize, entries.size());
        for (String rdn : expectedRdns) {
            assertTrue("Expected RDN " + rdn + " not found", foundRdns.contains(rdn));
        }
    }

    private List<SearchRequest> findSearchRequests(String base, String filterContains) {
        return capturingSearchRequestHandler.getRequests().stream()
                .filter(request -> request.getBase().getName().equals(base) &&
                        request.getFilter().toString().contains(filterContains))
                .collect(Collectors.toList());
    }

    private void testSearchUsersRecursiveWithSharedGroups(Callable<List<Entry>> ldapSearch) throws Exception {
        Map<String, String> config = createRecursiveConfig(5);
        Set<String> expectedGroups = Set.of(
                "cn=level1Group,ou=groups,dc=proxy,dc=com",
                "cn=level2Group,ou=groups,dc=proxy,dc=com",
                "cn=level3Group,ou=groups,dc=proxy,dc=com",
                "cn=level4Group,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupA,ou=groups,dc=proxy,dc=com",
                "cn=cycleGroupB,ou=groups,dc=proxy,dc=com");

        testSearchUsersRecursiveSharedGroups(config, ldapSearch, 2, expectedGroups, 6);
    }

    private void testSearchUsersRecursiveSharedGroupsViaMemberOf(Callable<List<Entry>> ldapSearch) throws Exception {
        Map<String, String> config = createRecursiveConfigForMemberOf(5);
        Set<String> expectedGroups = Set.of(
                "cn=memberOflevel1,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel2,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel3,ou=groups,dc=proxy,dc=com",
                "cn=memberOflevel4,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleA,ou=groups,dc=proxy,dc=com",
                "cn=memberOfCycleB,ou=groups,dc=proxy,dc=com");

        testSearchUsersRecursiveSharedGroups(config, ldapSearch, 2, expectedGroups, 6);
    }

    private void testSearchUsersRecursiveSharedGroups(Map<String,String> config, Callable<List<Entry>> ldapSearch, int expectedEntries, Set<String> expectedGroups, int expectedCacheHits) throws Exception {
        final AtomicInteger cacheHits = new AtomicInteger(0);
        ldapProxyBackend = new LdapProxyBackend("testbackend", config) {
            @Override
            protected Map<String, Set<String>> createResolvedParentsCache() {
                return new HashMap<>() {
                    @Override
                    public Set<String> get(Object key) {
                        if (super.containsKey(key)) {
                            cacheHits.incrementAndGet();
                        }
                        return super.get(key);
                    }
                };
            }
        };

        // Search for all recursive users (recursiveUser and recursiveUser2)
        // They share level1Group, cycleGroupA, and all their ancestors.
        List<Entry> entries = ldapSearch.call();
        assertEquals(expectedEntries, entries.size());

        for (Entry entry : entries) {
            validateMemberOf(entry, expectedGroups);
        }

        // Verify that caching actually happened.
        // For the second user, many groups should have been found in the cache.
        assertEquals("Expected " + expectedCacheHits + " cache hits for shared groups, but got " + cacheHits.get(), expectedCacheHits, cacheHits.get());
    }

    private static class CapturingSearchRequestHandler extends LdapRequestHandler<SearchRequest> {
        private final LdapRequestHandler<SearchRequest> delegate;
        private final List<SearchRequest> requests = Collections.synchronizedList(new ArrayList<>());

        CapturingSearchRequestHandler(LdapRequestHandler<SearchRequest> delegate) {
            this.delegate = delegate;
        }

        public void reset() {
            requests.clear();
        }

        public List<SearchRequest> getRequests() {
            return List.copyOf(requests);
        }

        @Override
        public void handle(LdapSession session, SearchRequest message) throws Exception {
            requests.add(message);
            delegate.handle(session, message);
        }
    }

    private static class CapturingBindRequestHandler extends LdapRequestHandler<BindRequest> {
        private final LdapRequestHandler<BindRequest> delegate;
        private final List<BindRequest> requests = Collections.synchronizedList(new ArrayList<>());

        CapturingBindRequestHandler(LdapRequestHandler<BindRequest> delegate) {
            this.delegate = delegate;
        }

        public void reset() {
            requests.clear();
        }

        public List<BindRequest> getRequests() {
            return List.copyOf(requests);
        }

        @Override
        public void handle(LdapSession session, BindRequest message) throws Exception {
            requests.add(message);
            delegate.handle(session, message);
        }
    }
}
