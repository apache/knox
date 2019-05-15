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
package org.apache.knox.gateway.hadoopauth.filter;

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

/*
 * see http://hadoop.apache.org/docs/current/hadoop-auth/Configuration.html
 *
 * CONFIG_PREFIX = "config.prefix
 * AUTH_TYPE = "type", AUTH_TOKEN_VALIDITY = "token.validity"
 * COOKIE_DOMAIN = "cookie.domain", COOKIE_PATH = "cookie.path"
 * SIGNATURE_SECRET = "signature.secret
 * TYPE = "kerberos", PRINCIPAL = TYPE + ".principal", KEYTAB = TYPE + ".keytab"

 * config.prefix=hadoop.auth.config (default: null)
 * hadoop.auth.config.signature.secret=SECRET (default: a simple random number)
 * hadoop.auth.config.type=simple|kerberos|CLASS (default: none, would throw exception)
 * hadoop.auth.config.token.validity=SECONDS (default: 3600 seconds)
 * hadoop.auth.config.cookie.domain=DOMAIN(default: null)
 * hadoop.auth.config.cookie.path=PATH (default: null)
 * hadoop.auth.config.kerberos.principal=HTTP/localhost@LOCALHOST (default: null)
 * hadoop.auth.config.kerberos.keytab=/etc/knox/conf/knox.service.keytab (default: null)
 */

public class HadoopAuthFilter extends
    org.apache.hadoop.security.authentication.server.AuthenticationFilter {

  @Override
  protected Properties getConfiguration(String configPrefix, FilterConfig filterConfig) throws ServletException {
    GatewayServices services = GatewayServer.getGatewayServices();
    AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);

    return getConfiguration(aliasService, configPrefix, filterConfig);
  }

  // Visible for testing
  Properties getConfiguration(AliasService aliasService, String configPrefix,
                                        FilterConfig filterConfig) throws ServletException {

    String clusterName = filterConfig.getInitParameter("clusterName");

    Properties props = new Properties();
    Enumeration<String> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.startsWith(configPrefix)) {
        String value = filterConfig.getInitParameter(name);

        // Handle the case value is an alias
        if (value.startsWith("${ALIAS=") && value.endsWith("}")) {
          String alias = value.substring("${ALIAS=".length(), value.length() - 1);
          try {
            value = String.valueOf(
                aliasService.getPasswordFromAliasForCluster(clusterName, alias));
          } catch (AliasServiceException e) {
            throw new ServletException("Unable to retrieve alias for config: " + name, e);
          }
        }

        props.put(name.substring(configPrefix.length()), value);
      }
    }
    return props;
  }
}
