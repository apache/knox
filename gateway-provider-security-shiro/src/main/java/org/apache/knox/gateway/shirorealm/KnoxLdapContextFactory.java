/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.knox.gateway.shirorealm;

import java.security.KeyStore;
import java.util.Hashtable;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.fips.FipsUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;
import org.apache.shiro.realm.ldap.JndiLdapContextFactory;

/**
 * An extension of {@link JndiLdapContextFactory} that allows a different authentication mechanism
 * for system-level authentications (as used by authorization lookups, for example)
 * compared to regular authentication.
 *
 * <p>
 * See {@link KnoxLdapRealm} for typical configuration within <tt>shiro.ini</tt>.
 */
public class KnoxLdapContextFactory extends JndiLdapContextFactory {

    private static GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
    static final String SOCKET_FACTORY_ENV = "java.naming.ldap.factory.socket";
    private static final String LDAPS_SCHEME = "ldaps://";
    private static final String SSL_SECURITY_PROTOCOL = "ssl";
    private static final AtomicBoolean LDAPS_SOCKET_FACTORY_INITIALIZED = new AtomicBoolean();
    private String systemAuthenticationMechanism = "simple";
    private String clusterName = "";

    public KnoxLdapContextFactory() {
        setAuthenticationMechanism("simple");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected LdapContext createLdapContext(Hashtable env) throws NamingException {
        if (getSystemUsername() != null && getSystemUsername().equals(env.get(Context.SECURITY_PRINCIPAL))) {
            env.put(Context.SECURITY_AUTHENTICATION, getSystemAuthenticationMechanism());
        }
        configureLdapsSocketFactory(env);
        return super.createLdapContext(env);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void configureLdapsSocketFactory(Hashtable env) {
        if (env.containsKey(SOCKET_FACTORY_ENV) || !isFipsEnabled() || !isLdaps(env)) {
            return;
        }
        initLdapsSocketFactory();
        if (KnoxLdapSslSocketFactory.getDelegate() != null) {
            env.put(SOCKET_FACTORY_ENV, KnoxLdapSslSocketFactory.class.getName());
        }
    }

    private void initLdapsSocketFactory() {
        if (LDAPS_SOCKET_FACTORY_INITIALIZED.get()) {
            return;
        }
        synchronized (KnoxLdapContextFactory.class) {
            if (LDAPS_SOCKET_FACTORY_INITIALIZED.get()) {
                return;
            }
            try {
                final KeyStore truststore = getHttpClientTruststore();
                if (truststore == null) {
                    LOG.noHttpClientTruststoreForLdaps();
                } else {
                    final SSLContext sslContext = TruststoreSSLContextUtils.getTruststoreSSLContext(truststore);
                    if (sslContext != null) {
                        KnoxLdapSslSocketFactory.setDelegate(sslContext.getSocketFactory());
                        LOG.usingHttpClientTruststoreForLdaps();
                    }
                }
            } catch (KeystoreServiceException | RuntimeException e) {
                LOG.failedToCreateLdapsSocketFactory(e.getMessage(), e);
            }
            LDAPS_SOCKET_FACTORY_INITIALIZED.set(true);
        }
    }

    protected boolean isFipsEnabled() {
        return FipsUtils.isFipsEnabledWithBCProvider();
    }

    protected KeyStore getHttpClientTruststore() throws KeystoreServiceException {
        final GatewayServices services = GatewayServer.getGatewayServices();
        if (services == null) {
            return null;
        }
        final KeystoreService keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
        return keystoreService == null ? null : keystoreService.getTruststoreForHttpClient();
    }

    @SuppressWarnings("rawtypes")
    static boolean isLdaps(Hashtable env) {
        final Object protocol = env.get(Context.SECURITY_PROTOCOL);
        if (protocol != null && SSL_SECURITY_PROTOCOL.equalsIgnoreCase(protocol.toString().trim())) {
            return true;
        }
        final Object url = env.get(Context.PROVIDER_URL);
        if (url == null || url.toString().trim().isEmpty()) {
            return false;
        }
        for (String candidate : url.toString().trim().split("\\s+")) {
            if (!candidate.toLowerCase(Locale.ROOT).startsWith(LDAPS_SCHEME)) {
                return false;
            }
        }
        return true;
    }

    static void resetLdapsSocketFactory() {
        synchronized (KnoxLdapContextFactory.class) {
            LDAPS_SOCKET_FACTORY_INITIALIZED.set(false);
            KnoxLdapSslSocketFactory.setDelegate(null);
        }
    }

    public String getSystemAuthenticationMechanism() {
        return systemAuthenticationMechanism != null ? systemAuthenticationMechanism : getAuthenticationMechanism();
    }

    public void setSystemAuthenticationMechanism(String systemAuthenticationMechanism) {
        this.systemAuthenticationMechanism = systemAuthenticationMechanism;
    }

    @Override
    public String getSystemPassword() {
        final String systemPass = super.getSystemPassword();
        final AliasService aliasService = getAliasService();

        if (StringUtils.isBlank(systemPass) || !aliasService.isAlias(systemPass)) {
            return systemPass;
        } else {
            final String systemPasswordAlias = aliasService.extractAlias(systemPass);
            char[] systemPassword = null;
            try {
                // try to locate system password in the topology-level credential store first
                systemPassword = aliasService.getPasswordFromAliasForCluster(clusterName, systemPasswordAlias);
                if (systemPassword == null) {
                    // fall back to gateway-level credential store
                    systemPassword = aliasService.getPasswordFromAliasForGateway(systemPasswordAlias);
                }
            } catch (AliasServiceException e) {
                LOG.unableToGetPassword(e);
            }
            if (systemPassword != null) {
                return new String(systemPassword);
            } else {
                LOG.aliasValueNotFound(clusterName, systemPasswordAlias);
                return ""; //needs to be set to blank
            }
        }
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        if (clusterName != null) {
            this.clusterName = clusterName.trim();
        }
    }

    protected AliasService getAliasService() {
        final GatewayServices services = GatewayServer.getGatewayServices();
        return services.getService(ServiceType.ALIAS_SERVICE);
    }
}
