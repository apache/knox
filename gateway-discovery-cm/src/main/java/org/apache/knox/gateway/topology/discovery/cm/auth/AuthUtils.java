/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.auth;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscoveryMessages;

import javax.security.auth.Subject;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.io.File;

public class AuthUtils {

  static final String JGSS_LOGIN_MODULE = "com.sun.security.jgss.initiate";

  private static ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static Configuration kerberosConfig;

  public static String getKerberosLoginConfigLocation() {
    return System.getProperty(GatewayConfig.KRB5_LOGIN_CONFIG);
  }

  public static synchronized Configuration getKerberosJAASConfiguration() throws Exception {
    if (kerberosConfig == null) {
      String configLocation = getKerberosLoginConfigLocation();
      if (configLocation != null && !configLocation.isEmpty()) {
        kerberosConfig = createKerberosJAASConfiguration(configLocation);
      }
    }
    return kerberosConfig;
  }

  public static Configuration createKerberosJAASConfiguration(String kerberosLoginConfig) throws Exception {
    if (kerberosLoginConfig == null) {
      throw new IllegalArgumentException("Invalid login configuration.");
    }
    return new JAASClientConfig((new File(kerberosLoginConfig)).toURI().toURL());
  }

  public static Subject getKerberosSubject() {
    Subject subject = null;
    try {
      Configuration jaasConf = getKerberosJAASConfiguration();
      if (jaasConf != null) {
        log.attemptingKerberosLogin(getKerberosLoginConfigLocation());
        LoginContext lc = new LoginContext(JGSS_LOGIN_MODULE, null, null, jaasConf);
        lc.login();
        subject = lc.getSubject();
      }
    } catch (Exception e) {
      log.failedKerberosLogin(getKerberosLoginConfigLocation(), JGSS_LOGIN_MODULE, e);
    }

    return subject;
  }

}
