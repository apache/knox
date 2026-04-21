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

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
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
        return super.createLdapContext(env);
    }

    public String getSystemAuthenticationMechanism() {
        return systemAuthenticationMechanism != null ? systemAuthenticationMechanism : getAuthenticationMechanism();
    }

    public void setSystemAuthenticationMechanism(String systemAuthenticationMechanism) {
        this.systemAuthenticationMechanism = systemAuthenticationMechanism;
    }

    @Override
    public void setSystemPassword(final String systemPass) {
        if (StringUtils.isBlank(systemPass)) {
            return;
        }

        final AliasService aliasService = getAliasService();

        if (!aliasService.isAlias(systemPass)) {
            super.setSystemPassword(systemPass);
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
                super.setSystemPassword(new String(systemPassword));
            } else {
                super.setSystemPassword(""); //needs to be set to blank
                LOG.aliasValueNotFound(clusterName, systemPasswordAlias);
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
