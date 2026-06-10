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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.ThumbprintUtils;
import com.nimbusds.jose.util.Base64URL;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TokenUtils {
  public static final String ATTR_CURRENT_KNOXSSO_COOKIE_TOKEN_ID = "currentKnoxSsoCookieTokenId";
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
    String providerParamValue = filterConfig.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED);
    return isServerManagedTokenStateEnabled(providerParamValue, filterConfig.getServletContext());
  }

  public static boolean isServerManagedTokenStateEnabled(ServletContext context) {
    final String serviceParamValue = context.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED);
    return isServerManagedTokenStateEnabled(serviceParamValue, context);
  }

  private static boolean isServerManagedTokenStateEnabled(String parameterValue, ServletContext context) {
    if (parameterValue == null || parameterValue.isEmpty()) {
      // Fall back to the gateway-level default
      GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      return (config != null) && config.isServerManagedTokenStateEnabled();
    } else {
      // Otherwise, apply the service-level configuration
      return Boolean.valueOf(parameterValue);
    }
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
   * Utility method to calculate public key thumbprint
   * @param publicKey
   * @param hashAlgorithm
   * @return
   * @throws JOSEException
   */
  public static String getThumbprint(final RSAPublicKey publicKey, final String hashAlgorithm)
      throws JOSEException {
    LinkedHashMap<String,String> params = new LinkedHashMap<>();
    params.put("e", Base64URL.encode(publicKey.getPublicExponent()).toString());
    params.put("kty", KeyType.RSA.getValue());
    params.put("n", Base64URL.encode(publicKey.getModulus()).toString());
    return ThumbprintUtils.compute(hashAlgorithm, params).toString();
  }

  /**
   * @return true, if the HMAC secret is configured via the alias service for the gateway AND signing keystore name is not set ; false
   *         otherwise
   */
  private static boolean useHMAC(char[] hmacSecret, String signingKeystoreName) {
    return hmacSecret != null && StringUtils.isBlank(signingKeystoreName);
  }

  /**
   * Extract the actor chain from an RFC 8693 'act' claim in a JWT token.
   *
   * <p>The 'act' claim in RFC 8693 represents a delegation chain where each actor
   * delegated authority to the next. The claim is structured as a nested JSON object,
   * where each level contains identity claims (such as 'sub' and 'iss') and potentially
   * another nested 'act' claim.</p>
   *
   * <p>According to RFC 8693 Section 4.1, the 'act' claim contains identity claims that
   * identify the actor. Common identity claims include:</p>
   * <ul>
   *   <li>'sub' - the subject/identity of the actor</li>
   *   <li>'iss' - the issuer of the actor's identity</li>
   * </ul>
   *
   * <p>Non-identity claims (e.g., 'exp', 'nbf', 'aud') are not relevant to the validity
   * of the containing JWT and should not be used within 'act' claims.</p>
   *
   * <p>Example JWT 'act' claim structure:</p>
   * <pre>
   * {
   *   "sub": "service-a",
   *   "iss": "https://issuer.example.com",
   *   "act": {
   *     "sub": "service-b",
   *     "act": {
   *       "sub": "service-c"
   *     }
   *   }
   * }
   * </pre>
   *
   * <p>This method flattens this nested structure into a list ordered from most recent
   * actor (service-a) to oldest (service-c).</p>
   *
   * @param token The JWT token to extract the actor chain from
   * @return A list of actor claim maps, ordered from most recent to oldest; empty list if no 'act' claim exists
   */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> extractActorChain(JWT token) {
    if (token == null) {
      return Collections.emptyList();
    }

    Object actClaim = token.getClaimAsObject(JWTToken.ACT_CLAIM);
    if (actClaim == null) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> actorChain = new ArrayList<>();

    // Recursively traverse the nested 'act' claims
    Object currentAct = actClaim;
    while (currentAct instanceof Map) {
      Map<String, Object> actorMap = (Map<String, Object>) currentAct;
      actorChain.add(new LinkedHashMap<>(actorMap));

      // Get the nested 'act' claim for the next iteration
      currentAct = actorMap.get(JWTToken.ACT_CLAIM);
    }

    return Collections.unmodifiableList(actorChain);
  }

  /**
   * Build a new actor chain by adding a new actor to an existing chain.
   *
   * <p>This creates a new list with the new actor as the first element (most recent),
   * followed by all actors from the existing chain.</p>
   *
   * @param existingChain The existing actor chain (may be null or empty)
   * @param newActor The new actor to add to the chain
   * @return A new immutable list with the complete actor chain
   */
  public static List<Map<String, Object>> addActorToChain(List<Map<String, Object>> existingChain, String newActor) {
    if (newActor == null || newActor.isEmpty()) {
      return existingChain == null ? Collections.emptyList() : existingChain;
    }

    List<Map<String, Object>> newChain = new ArrayList<>();

    // Add the new actor as the first element
    Map<String, Object> newActorClaim = new LinkedHashMap<>();
    newActorClaim.put("sub", newActor);
    newChain.add(newActorClaim);

    // Add all existing actors
    if (existingChain != null && !existingChain.isEmpty()) {
      newChain.addAll(existingChain);
    }

    return Collections.unmodifiableList(newChain);
  }

  /**
   * Convert an actor chain list into the nested structure required for the JWT 'act' claim.
   *
   * <p>This method takes a flat list of actor claim maps (ordered from most recent to oldest)
   * and converts it into the nested structure required by RFC 8693.</p>
   *
   * <p>Example transformation:</p>
   * <pre>
   * Input:  [{"sub": "actor1"}, {"sub": "actor2"}, {"sub": "actor3"}]
   * Output: {
   *   "sub": "actor1",
   *   "act": {
   *     "sub": "actor2",
   *     "act": {
   *       "sub": "actor3"
   *     }
   *   }
   * }
   * </pre>
   *
   * @param actorChain The flat list of actor claim maps, ordered from most recent to oldest
   * @return The nested structure for the 'act' claim, or null if the chain is empty
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> buildNestedActClaim(List<Map<String, Object>> actorChain) {
    if (actorChain == null || actorChain.isEmpty()) {
      return null;
    }

    // Start from the last actor (oldest) and work backwards
    Map<String, Object> nestedAct = null;
    for (int i = actorChain.size() - 1; i >= 0; i--) {
      Map<String, Object> currentActor = new LinkedHashMap<>(actorChain.get(i));

      if (nestedAct != null) {
        // Add the previously built nested structure as the 'act' claim
        currentActor.put(JWTToken.ACT_CLAIM, nestedAct);
      }

      nestedAct = currentActor;
    }

    return nestedAct;
  }

}
