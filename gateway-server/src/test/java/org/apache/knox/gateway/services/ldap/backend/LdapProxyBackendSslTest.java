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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapTlsHandshakeException;
import org.apache.directory.api.ldap.model.name.Dn;
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
import org.apache.knox.gateway.util.X509CertificateUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verifies that {@link LdapProxyBackend} can talk to a remote LDAP server over a secure
 * (LDAPS) transport. An embedded ApacheDS instance is started with SSL enabled and the backend
 * connects to it over TLS for both searches (pooled connections) and authentication binds.
 */
public class LdapProxyBackendSslTest {
    private static final String KEYSTORE_PASSWORD = "keystore-password";

    private static File keystore;
    private static TcpTransport transport;
    private static DirectoryService directoryService;
    private static LdapServer ldapServer;
    private static SchemaManager schemaManager;
    private static int port;

    private LdapProxyBackend ldapProxyBackend;

    @BeforeClass
    public static void setupBeforeClass() throws Exception {
        keystore = createTempKeystore();

        directoryService = new SimpleDirectoryService();
        directoryService.setShutdownHookEnabled(false);
        schemaManager = SchemaManagerFactory.createSchemaManager();
        directoryService.setSchemaManager(schemaManager);

        String instanceDirectory = System.getProperty("java.io.tmpdir", "/tmp") + "/server-work-ssl-" + UUID.randomUUID();
        directoryService.setInstanceLayout(new InstanceLayout(instanceDirectory));

        File workingDirectory = directoryService.getInstanceLayout().getPartitionsDirectory();
        LdifPartition ldifPartition = new LdifPartition(schemaManager, directoryService.getDnFactory());
        ldifPartition.setPartitionPath(new File(workingDirectory, "schema").toURI());
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

        directoryService.startup();

        CoreSession session = directoryService.getAdminSession();
        File ldifFile = new File(LdapProxyBackendSslTest.class.getResource("/ldap-proxy-backend-test.ldif").toURI());
        new LdifFileLoader(session, ldifFile, null).execute();

        // Start the embedded server with a secure (LDAPS) transport.
        transport = new TcpTransport(0);
        transport.setEnableSSL(true);
        ldapServer = new LdapServer();
        ldapServer.setKeystoreFile(keystore.getAbsolutePath());
        ldapServer.setCertificatePassword(KEYSTORE_PASSWORD);
        ldapServer.setTransports(transport);
        ldapServer.setDirectoryService(directoryService);
        ldapServer.start();
        port = transport.getAcceptor().getLocalAddress().getPort();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        if (ldapServer != null) {
            ldapServer.stop();
        }
        if (directoryService != null) {
            directoryService.shutdown();
        }
        if (keystore != null) {
            keystore.delete();
        }
    }

    @After
    public void tearDown() {
        if (ldapProxyBackend != null) {
            ldapProxyBackend.close();
        }
    }

    @Test
    public void testGetUserOverLdaps() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("sslbackend", trustingConfig());

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        assertNotNull("User should be resolved over LDAPS", entry);
        assertEquals("ldaptest1", entry.get("uid").getString());
        validateMemberOf(entry, Set.of(
                "cn=group1,ou=groups,dc=hadoop,dc=apache,dc=org",
                "cn=group2,ou=groups,dc=hadoop,dc=apache,dc=org"));
    }

    @Test
    public void testGetUserGroupsOverLdaps() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("sslbackend", trustingConfig());

        List<String> groups = ldapProxyBackend.getUserGroups("ldaptest1", schemaManager);
        assertTrue(groups.contains("group1"));
        assertTrue(groups.contains("group2"));
    }

    @Test
    public void testAuthenticateOverLdaps() throws Exception {
        ldapProxyBackend = new LdapProxyBackend("sslbackend", trustingConfig());

        Dn dn = new Dn("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
        assertTrue("Bind over LDAPS should succeed", ldapProxyBackend.authenticate(dn, "guest-password"));
        assertFalse("Bind over LDAPS with wrong password should fail",
                ldapProxyBackend.authenticate(dn, "wrong-password"));
    }

    @Test
    public void testExplicitUseSslWithoutUrlScheme() throws Exception {
        // Enable LDAPS via the useSsl flag rather than the ldaps:// scheme (host/port style config).
        Map<String, String> config = new HashMap<>(baseConfig());
        config.remove("url");
        config.put("host", "localhost");
        config.put("port", String.valueOf(port));
        config.put("useSsl", "true");
        config.put("trustAllCertificates", "true");
        ldapProxyBackend = new LdapProxyBackend("sslbackend", config);

        Entry entry = ldapProxyBackend.getUser("ldaptest1", schemaManager);
        assertNotNull("User should be resolved over LDAPS configured via useSsl flag", entry);
    }

    @Test(expected = LdapTlsHandshakeException.class)
    public void testUntrustedCertificateIsRejected() throws Exception {
        // useSsl without trusting the self-signed cert: the JVM default trust store must reject it,
        // proving certificate validation is actually enforced.
        Map<String, String> config = new HashMap<>(baseConfig());
        config.put("trustAllCertificates", "false");
        config.put("connectionTimeout", "5000");
        ldapProxyBackend = new LdapProxyBackend("sslbackend", config);

        ldapProxyBackend.getUser("ldaptest1", schemaManager);
        fail("Connecting over LDAPS to a server with an untrusted certificate should fail");
    }

    private Map<String, String> baseConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("baseDn", "dc=hadoop,dc=apache,dc=org");
        config.put("remoteBaseDn", "dc=hadoop,dc=apache,dc=org");
        config.put("url", "ldaps://localhost:" + port);
        config.put("systemUsername", "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
        config.put("systemPassword", "guest-password");
        config.put("userSearchBase", "ou=people,dc=hadoop,dc=apache,dc=org");
        config.put("groupSearchBase", "ou=groups,dc=hadoop,dc=apache,dc=org");
        return config;
    }

    private Map<String, String> trustingConfig() {
        Map<String, String> config = new HashMap<>(baseConfig());
        config.put("trustAllCertificates", "true");
        return config;
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

    /**
     * Build a keystore holding a self-signed certificate and its private key, in the JVM default
     * keystore format (which is what ApacheDS uses to load the server certificate).
     */
    private static File createTempKeystore() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        X509Certificate cert = X509CertificateUtil.generateCertificate(
                "CN=localhost, OU=Test, O=Knox, L=Test, ST=Test, C=US", keyPair, 365, "SHA256withRSA");
        assertNotNull("Failed to generate a self-signed test certificate", cert);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("knox-ldaps", keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
                new Certificate[] { cert });

        File file = File.createTempFile("knox-ldaps-backend", ".ks");
        file.deleteOnExit();
        try (OutputStream os = Files.newOutputStream(file.toPath())) {
            keyStore.store(os, KEYSTORE_PASSWORD.toCharArray());
        }
        return file;
    }
}
