/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WhitelistUtils {
  static final String DEFAULT_CONFIG_VALUE = "DEFAULT";
  static final String DEFAULT_DISPATCH_WHITELIST_TEMPLATE = "^\\/.*$;^https?:\\/\\/%s:[0-9]+\\/?.*$";

  static final String HTTPS_ONLY_CONFIG_VALUE = "HTTPS_ONLY";
  static final String HTTPS_ONLY_DISPATCH_WHITELIST_TEMPLATE = "^\\/.*$;^https:\\/\\/%s:[0-9]+\\/?.*$";

  static final String LOCALHOST_REGEXP_SEGMENT = "(localhost|127\\.0\\.0\\.1|0:0:0:0:0:0:0:1|::1)";
  static final String LOCALHOST_REGEXP = "^" + LOCALHOST_REGEXP_SEGMENT + "$";

  private static final String IP_ADDRESS_REGEX = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$";

  private static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);

  private static final List<String> DEFAULT_SERVICE_ROLES = Collections.singletonList("KNOXSSO");

  public static String getDispatchWhitelist(HttpServletRequest request) {
    String whitelist = null;

    GatewayConfig config = (GatewayConfig) request.getServletContext()
                                               .getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    if (config != null) {
      List<String> whitelistedServiceRoles = new ArrayList<>();
      whitelistedServiceRoles.addAll(DEFAULT_SERVICE_ROLES);
      whitelistedServiceRoles.addAll(config.getDispatchWhitelistServices());

      String serviceRole = (String) request.getAttribute("targetServiceRole");
      if (whitelistedServiceRoles.contains(serviceRole)) {
        // Check the whitelist against the URL to be dispatched
        whitelist = config.getDispatchWhitelist();
        if (whitelist == null || whitelist.equalsIgnoreCase(DEFAULT_CONFIG_VALUE)) {
          whitelist = deriveDefaultDispatchWhitelist(request, DEFAULT_DISPATCH_WHITELIST_TEMPLATE);
          LOG.derivedDispatchWhitelist(whitelist);
        } else if (whitelist.equalsIgnoreCase(HTTPS_ONLY_CONFIG_VALUE)) {
          whitelist = deriveDefaultDispatchWhitelist(request, HTTPS_ONLY_DISPATCH_WHITELIST_TEMPLATE);
          LOG.derivedDispatchWhitelist(whitelist);
        }
      }
    }

    return whitelist;
  }

  private static String deriveDefaultDispatchWhitelist(HttpServletRequest request,
                                                       String whitelistTemplate) {
    String defaultWhitelist = null;

    // Check first for the X-Forwarded-Host header, and use it to determine the domain
    String domain = getDomain(request.getHeader("X-Forwarded-Host"));

    // If a domain has still not yet been determined, try the requested host name
    String requestedHost = null;

    if (domain == null) {
      requestedHost = request.getServerName();
      domain = getDomain(requestedHost);
    }

    if (domain != null) {
      defaultWhitelist = defineWhitelistForDomain(domain, whitelistTemplate);
    } else {
      if (!requestedHost.matches(LOCALHOST_REGEXP)) { // localhost will be handled subsequently
        // Use the requested host address/name for the whitelist
        LOG.unableToDetermineKnoxDomainForDefaultWhitelist(requestedHost);
        defaultWhitelist = String.format(Locale.ROOT, whitelistTemplate, requestedHost);
      }
    }

    // If the whitelist has not been determined at this point, default to just the local/relative whitelist
    if (defaultWhitelist == null) {
      LOG.unableToDetermineKnoxDomainForDefaultWhitelist("localhost");
      defaultWhitelist = String.format(Locale.ROOT, whitelistTemplate, LOCALHOST_REGEXP_SEGMENT);
    }

    return defaultWhitelist;
  }

  private static String getDomain(String hostname) {
    String domain = null;

    if (hostname != null && !hostname.isEmpty()) {
      // The value may include port information, which needs to be removed
      hostname = stripPort(hostname);

      if (!hostname.matches(IP_ADDRESS_REGEX)) {
        int domainIndex = hostname.indexOf('.');
        if (domainIndex > 0) {
          domain = hostname.substring(hostname.indexOf('.'));
        }
      }
    }

    return domain;
  }

  private static String defineWhitelistForDomain(String domain, String whitelistTemplate) {
    String whitelist = null;

    if (domain != null && !domain.isEmpty()) {
      String domainPattern = ".+" + domain.replaceAll("\\.", "\\\\.");
      whitelist = String.format(Locale.ROOT, whitelistTemplate, "(" + domainPattern + ")");
    }

    return whitelist;
  }

  private static String stripPort(String hostName) {
    String result = hostName;

    int portIndex = hostName.indexOf(':');
    if (portIndex > 0) {
      result = hostName.substring(0, portIndex);
    }

    return result;
  }
}
