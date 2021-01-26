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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;


public class TokenUtils {
  public static final String SIGNING_HMAC_SECRET_ALIAS = "gateway.signing.hmac.secret";
  private static final String DEFAULT_RSA_SIG_ALG = "RS256";
  private static final String DEFAULT_HMAC_SIG_ALG = "HS256";

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

  /**
   * @return <code>configuredSignatureAlgorithm</code> if any OR the default HMAC algorithm if {@link #useHMAC(char[], String)} is
   *         <code>true</code>; the default RSA algorithm otherwise
   */
  public static String getSignatureAlgorithm(String configuredSignatureAlgorithm, AliasService aliasService, String signingKeystoreName) throws AliasServiceException {
    if (StringUtils.isNotBlank(configuredSignatureAlgorithm)) {
      return configuredSignatureAlgorithm;
    } else {
      final char[] hmacSecret = aliasService.getPasswordFromAliasForGateway(SIGNING_HMAC_SECRET_ALIAS);
      return useHMAC(hmacSecret == null ? null : hmacSecret, signingKeystoreName) ? DEFAULT_HMAC_SIG_ALG : DEFAULT_RSA_SIG_ALG;
    }
  }

  /**
   * @return true, if the HMAC secret is configured via the alias service for the gateway AND signing keystore name is not set ; false
   *         otherwise
   */
  private static boolean useHMAC(char[] hmacSecret, String signingKeystoreName) {
    return hmacSecret != null && StringUtils.isBlank(signingKeystoreName);
  }

}
