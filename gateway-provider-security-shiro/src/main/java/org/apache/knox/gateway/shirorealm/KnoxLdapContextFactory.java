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

    private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

    private String systemAuthenticationMechanism = "simple";
    private String clusterName = "";

    public KnoxLdapContextFactory() {
      setAuthenticationMechanism("simple");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected LdapContext createLdapContext(Hashtable env) throws NamingException {
        if(getSystemUsername() != null && getSystemUsername().equals(env.get(Context.SECURITY_PRINCIPAL))) {
            env.put(Context.SECURITY_AUTHENTICATION, getSystemAuthenticationMechanism());
        }
        return super.createLdapContext(env);
    }

    public String getSystemAuthenticationMechanism() {
        return systemAuthenticationMechanism != null? systemAuthenticationMechanism: getAuthenticationMechanism();
    }

    public void setSystemAuthenticationMechanism(String systemAuthenticationMechanism) {
        this.systemAuthenticationMechanism = systemAuthenticationMechanism;
    }

    @Override
    public void setSystemPassword(String systemPass) {
      if ( systemPass == null ) {
        return;
      }

      systemPass = systemPass.trim();
      if (systemPass.isEmpty()) {
        return;
      }

      if (!systemPass.startsWith("S{ALIAS=")) {
        super.setSystemPassword( systemPass );
        return;
      }

      systemPass= systemPass.substring( "S{ALIAS=".length(), systemPass.length() - 1 );
      String aliasName = systemPass;

      GatewayServices services = GatewayServer.getGatewayServices();
      AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);

      String clusterName = getClusterName();
      //System.err.println("FACTORY systempass 30: " + systemPass);
      //System.err.println("FACTORY clustername 40: " + clusterName);
      //System.err.println("FACTORY SystemProperty GatewayHome 50: " + System.getProperty(GatewayConfig.GATEWAY_HOME_VAR));
      char[] password = null;
      try {
        password = aliasService.getPasswordFromAliasForCluster(clusterName, systemPass);
      } catch (AliasServiceException e) {
        LOG.unableToGetPassword(e);
      }
      //System.err.println("FACTORY password: " + ((password == null) ? "NULL" : new String(password)));
      if ( password != null ) {
        //System.err.println("FACTORY SUCCESS 20 system password :" + new String(password));
        super.setSystemPassword( new String(password) );
      } else {
        //System.err.println("FACTORY FORCING system password to blank");
        super.setSystemPassword("" );
        LOG.aliasValueNotFound(clusterName, aliasName);
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
}
