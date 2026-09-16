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
package org.apache.knox.gateway.service.knoxtoken;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.TokenUtils;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JWKSResource.RESOURCE_PATH)
public class JWKSResource {
  public static final String JWKS_PATH = "/jwks.json";
  static final String RESOURCE_PATH = "knoxtoken/api/v1";
  private static final String TOKEN_SIG_ALG = "knox.token.sigalg";

  @Context
  HttpServletRequest request;
  @Context
  ServletContext context;
  private KeystoreService keystoreService;
  private String signatureAlgorithm;

  @PostConstruct
  public void init() throws AliasServiceException {
    final GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);

    final String configuredSigAlg = context.getInitParameter(TOKEN_SIG_ALG);
    final GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    this.signatureAlgorithm = TokenUtils.getSignatureAlgorithm(configuredSigAlg, (AliasService) services.getService(ServiceType.ALIAS_SERVICE), config.getSigningKeystoreName());
  }

  @GET
  @Path(JWKS_PATH)
  @Produces({ APPLICATION_JSON })
  public Response getJwksResponse() {
    return getJwks(null);
  }

  private Response getJwks(final String keystore) {
    try {
      // Publish one JWK per configured signing-key alias (current key first, then any additional
      // verification keys). Each JWK carries its own 'kid' (SHA-256 thumbprint) so a verifier can
      // select the right key across a key rotation. A single-key deployment yields exactly one JWK.
      final List<JWK> keys = new ArrayList<>();
      for (final String alias : getSigningKeyAliases()) {
        final RSAPublicKey rsa = getPublicKey(keystore, alias);
        /* no public cert for this alias, skip it */
        if (rsa == null) {
          continue;
        }
        final String kid = TokenUtils.getThumbprint(rsa, "SHA-256");
        keys.add(new RSAKey.Builder(rsa)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(new JWSAlgorithm(this.signatureAlgorithm))
            .keyID(kid)
            .build());
      }
      return Response.ok()
          .entity(new JWKSet(keys).toString()).type(MediaType.APPLICATION_JSON_TYPE).build();
    } catch (KeyStoreException | JOSEException e) {
      return Response.status(500)
          .entity("{\n  \"error\": \"" + e.toString() + "\"\n}\n").build();
    } catch (KeystoreServiceException e) {
      return Response.status(500).entity(
          "{\n  \"error\": \"" + "keystore " + keystore + " could not be found."
              + "\"\n}\n").build();
    }
  }

  protected RSAPublicKey getPublicKey(final String keystore) throws KeystoreServiceException, KeyStoreException {
    return getPublicKey(keystore, getSigningKeyAlias());
  }

  protected RSAPublicKey getPublicKey(final String keystore, final String alias) throws KeystoreServiceException, KeyStoreException {
    final KeyStore ks = keystoreService.getSigningKeystore(keystore);
    final Certificate cert = ks.getCertificate(alias);
    return (cert != null && cert.getPublicKey() instanceof RSAPublicKey) ? (RSAPublicKey) cert.getPublicKey() : null;
  }

  /**
   * @return the configured signing-key aliases to publish, falling back to the single default
   * signing key when none are configured (backward-compatible single-key behavior).
   */
  private List<String> getSigningKeyAliases() {
    final GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final List<String> aliases = (config == null) ? null : config.getSigningKeyAliases();
    return (aliases == null || aliases.isEmpty()) ? Collections.singletonList(getSigningKeyAlias()) : aliases;
  }

  private String getSigningKeyAlias() {
    final GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final String alias = (config == null) ? null : config.getSigningKeyAlias();
    return (alias == null) ? GatewayConfig.DEFAULT_SIGNING_KEY_ALIAS : alias;
  }

}