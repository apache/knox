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
package org.apache.knox.gateway.shirorealm;

import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.naming.Context;
import javax.net.ssl.SSLSocketFactory;
import java.security.KeyStore;
import java.util.Hashtable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class KnoxLdapContextFactoryTest {

    private AliasService aliasService;

    @Before
    public void setup() {
        aliasService = EasyMock.createNiceMock(AliasService.class);
        KnoxLdapContextFactory.resetLdapsSocketFactory();
    }

    @After
    public void tearDown() {
        KnoxLdapContextFactory.resetLdapsSocketFactory();
    }

    /**
     * Test-specific subclass to avoid using GatewayServer static method
     */
    private class TestKnoxLdapContextFactory extends KnoxLdapContextFactory {
        @Override
        protected AliasService getAliasService() {
            return aliasService;
        }
    }

    @Test
    public void testGetSystemPasswordBlank() {
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        factory.setSystemPassword("");
        assertEquals("", factory.getSystemPassword());
    }

    @Test
    public void testGetSystemPasswordNull() {
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        factory.setSystemPassword(null);
        assertNull(factory.getSystemPassword());
    }

    @Test
    public void testGetSystemPasswordIsNotAlias() {
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        final String password = "password";
        factory.setSystemPassword(password);
        assertEquals(password, factory.getSystemPassword());
    }

    @Test
    public void testGetSystemPasswordIsAliasFoundInClusterCredentialStore() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.CLUSTER);
    }

    @Test
    public void testGetSystemPasswordIsAliasFoundInGatewayCredentialStore() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.GATEWAY);
    }

    @Test
    public void testGetSystemPasswordNotFound() throws Exception {
        testAliasFoundInCredentialStore(PasswordLocation.NOWHERE);
    }

    private enum PasswordLocation {
        CLUSTER, GATEWAY, NOWHERE;
    }

    private void testAliasFoundInCredentialStore(final PasswordLocation location) throws AliasServiceException {
        final String clusterName = "test-cluster";
        final KnoxLdapContextFactory factory = new TestKnoxLdapContextFactory();
        factory.setClusterName(clusterName);
        final String systemPasswordAlias = "systemPasswordAlias";
        final String systemPassword = AliasService.ALIAS_PREFIX + systemPasswordAlias + "}";
        final char[] resolvedPassword = "resolved-password".toCharArray();

        EasyMock.expect(aliasService.isAlias(systemPassword)).andReturn(true).anyTimes();
        EasyMock.expect(aliasService.extractAlias(systemPassword)).andReturn(systemPasswordAlias).anyTimes();
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(clusterName, systemPasswordAlias)).andReturn(location == PasswordLocation.CLUSTER ? resolvedPassword : null);
        if (location != PasswordLocation.CLUSTER) {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(systemPasswordAlias)).andReturn(location == PasswordLocation.GATEWAY ? resolvedPassword : null);
        }
        EasyMock.replay(aliasService);

        factory.setSystemPassword(systemPassword);
        if (location == PasswordLocation.NOWHERE) {
            assertEquals("", factory.getSystemPassword());
        } else {
            assertEquals(new String(resolvedPassword), factory.getSystemPassword());
        }

        EasyMock.verify(aliasService);
    }

    @Test
    public void testFipsLdapsUsesHttpClientTruststore() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, emptyTruststore());
        final Hashtable<String, Object> env = env("ldaps://ad.example.com:636");

        factory.configureLdapsSocketFactory(env);

        assertEquals(KnoxLdapSslSocketFactory.class.getName(), env.get(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
        final SSLSocketFactory delegate = KnoxLdapSslSocketFactory.getDelegate();
        assertNotNull(delegate);
        final KnoxLdapSslSocketFactory socketFactory = (KnoxLdapSslSocketFactory) KnoxLdapSslSocketFactory.getDefault();
        assertSame(delegate, socketFactory.getWrappedFactory());
    }

    @Test
    public void testTruststoreIsLoadedOnlyOnce() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, emptyTruststore());

        factory.configureLdapsSocketFactory(env("ldaps://ad.example.com"));
        factory.configureLdapsSocketFactory(env("ldaps://ad.example.com"));

        assertEquals(1, factory.truststoreLookups);
    }

    @Test
    public void testFipsLdapsWithoutTruststoreKeepsJvmDefault() {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, null);
        final Hashtable<String, Object> env = env("ldaps://ad.example.com");

        factory.configureLdapsSocketFactory(env);
        factory.configureLdapsSocketFactory(env);

        assertFalse(env.containsKey(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
        assertEquals(1, factory.truststoreLookups);
        assertNull(KnoxLdapSslSocketFactory.getDelegate());
        final KnoxLdapSslSocketFactory socketFactory = (KnoxLdapSslSocketFactory) KnoxLdapSslSocketFactory.getDefault();
        assertNotNull(socketFactory.getWrappedFactory());
    }

    @Test
    public void testNonFipsIsNotChanged() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(false, emptyTruststore());
        final Hashtable<String, Object> env = env("ldaps://ad.example.com");

        factory.configureLdapsSocketFactory(env);

        assertFalse(env.containsKey(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
        assertEquals(0, factory.truststoreLookups);
    }

    @Test
    public void testPlainLdapIsNotChanged() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, emptyTruststore());
        final Hashtable<String, Object> env = env("ldap://ad.example.com:389");

        factory.configureLdapsSocketFactory(env);

        assertFalse(env.containsKey(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
        assertEquals(0, factory.truststoreLookups);
    }

    @Test
    public void testMixedUrlsAreNotChanged() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, emptyTruststore());
        final Hashtable<String, Object> env = env("ldaps://ad1.example.com ldap://ad2.example.com");

        factory.configureLdapsSocketFactory(env);

        assertFalse(env.containsKey(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
    }

    @Test
    public void testExplicitSocketFactoryIsKept() throws Exception {
        final TruststoreTestContextFactory factory = new TruststoreTestContextFactory(true, emptyTruststore());
        final Hashtable<String, Object> env = env("ldaps://ad.example.com");
        env.put(KnoxLdapContextFactory.SOCKET_FACTORY_ENV, "com.example.CustomSocketFactory");

        factory.configureLdapsSocketFactory(env);

        assertEquals("com.example.CustomSocketFactory", env.get(KnoxLdapContextFactory.SOCKET_FACTORY_ENV));
        assertNull(KnoxLdapSslSocketFactory.getDelegate());
    }

    @Test
    public void testIsLdaps() {
        assertTrue(KnoxLdapContextFactory.isLdaps(env("ldaps://ad.example.com")));
        assertTrue(KnoxLdapContextFactory.isLdaps(env("LDAPS://ad.example.com:636")));
        assertTrue(KnoxLdapContextFactory.isLdaps(env(" ldaps://ad1.example.com  ldaps://ad2.example.com ")));
        assertFalse(KnoxLdapContextFactory.isLdaps(env("ldap://ad.example.com")));
        assertFalse(KnoxLdapContextFactory.isLdaps(env("ldaps://ad1.example.com ldap://ad2.example.com")));
        assertFalse(KnoxLdapContextFactory.isLdaps(env(" ")));
        assertFalse(KnoxLdapContextFactory.isLdaps(new Hashtable<String, Object>()));

        final Hashtable<String, Object> sslProtocol = env("ldap://ad.example.com:636");
        sslProtocol.put(Context.SECURITY_PROTOCOL, "ssl");
        assertTrue(KnoxLdapContextFactory.isLdaps(sslProtocol));
    }

    private static Hashtable<String, Object> env(String url) {
        final Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.PROVIDER_URL, url);
        return env;
    }

    private static KeyStore emptyTruststore() throws Exception {
        final KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        truststore.load(null, null);
        return truststore;
    }

    private static class TruststoreTestContextFactory extends KnoxLdapContextFactory {
        private final boolean fips;
        private final KeyStore truststore;
        private int truststoreLookups;

        TruststoreTestContextFactory(boolean fips, KeyStore truststore) {
            this.fips = fips;
            this.truststore = truststore;
        }

        @Override
        protected boolean isFipsEnabled() {
            return fips;
        }

        @Override
        protected KeyStore getHttpClientTruststore() {
            truststoreLookups++;
            return truststore;
        }
    }
}
