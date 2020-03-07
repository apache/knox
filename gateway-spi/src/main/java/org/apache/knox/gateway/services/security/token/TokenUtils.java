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
package org.apache.knox.gateway.services.security.token;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Locale;


public class TokenUtils {

  /**
   * Get a String derived from a JWT String, which is suitable for presentation (e.g., logging) without compromising
   * security.
   *
   * @param token A BASE64-encoded JWT String.
   *
   * @return An abbreviated form of the specified JWT String.
   */
  public static String getTokenDisplayText(final String token) {
    return String.format(Locale.ROOT, "%s...%s", token.substring(0, 6), token.substring(token.length() - 6));
  }

  /**
   * Extract the unique Knox token identifier from the specified JWT's claim set.
   *
   * @param token A JWT
   *
   * @return The unique identifier, or null.
   */
  public static String getTokenId(final JWT token) {
    return token.getClaim(JWTToken.KNOX_ID_CLAIM);
  }

  /**
   * Determine if server-managed token state is enabled for a provider, based on configuration.
   * The analysis includes checking the provider params and the gateway configuration.
   *
   * @param filterConfig A FilterConfig object.
   *
   * @return true, if server-managed state is enabled; Otherwise, false.
   */
  public static boolean isServerManagedTokenStateEnabled(FilterConfig filterConfig) {
    boolean isServerManaged = false;

    // First, check for explicit provider-level configuration
    String providerParamValue = filterConfig.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED);

    // If there is no provider-level configuration
    if (providerParamValue == null || providerParamValue.isEmpty()) {
      // Fall back to the gateway-level default
      ServletContext context = filterConfig.getServletContext();
      if (context != null) {
        GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        isServerManaged = (config != null) && config.isServerManagedTokenStateEnabled();
      }
    } else {
      // Otherwise, apply the provider-level configuration
      isServerManaged = Boolean.valueOf(providerParamValue);
    }

    return isServerManaged;
  }

}
