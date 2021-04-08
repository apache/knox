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
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Singleton
@Path(JWKSResource.RESOURCE_PATH)
public class JWKSResource {

  static final String RESOURCE_PATH = "knoxtoken/api/v1";
  static final String JWKS_PATH = "/jwks.json";
  @Context
  HttpServletRequest request;
  @Context
  ServletContext context;
  private KeystoreService keystoreService;

  @PostConstruct
  public void init() {
    final GatewayServices services = (GatewayServices) context
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
  }

  @GET
  @Path(JWKS_PATH)
  @Produces({ APPLICATION_JSON })
  public Response getJwksResponse() {
    return getJwks(null);
  }

  private Response getJwks(final String keystore) {
    JWKSet jwks;
    try {
      final RSAPublicKey rsa = getPublicKey(keystore);
      /* no public cert found, return empty set */
      if(rsa == null) {
        return Response.ok()
            .entity(new JWKSet().toJSONObject().toString()).build();
      }

      final RSAKey.Builder builder = new RSAKey.Builder(rsa)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(new JWSAlgorithm(rsa.getAlgorithm()))
          .keyIDFromThumbprint();

      jwks = new JWKSet(builder.build());

    } catch (KeyStoreException | JOSEException e) {
      return Response.status(500)
          .entity("{\n  \"error\": \"" + e.toString() + "\"\n}\n").build();
    } catch (KeystoreServiceException e) {
      return Response.status(500).entity(
          "{\n  \"error\": \"" + "keystore " + keystore + " could not be found."
              + "\"\n}\n").build();
    }
    return Response.ok()
        .entity(jwks.toJSONObject().toString()).build();
  }

  protected RSAPublicKey getPublicKey(final String keystore)
      throws KeystoreServiceException, KeyStoreException {
    final KeyStore ks = keystoreService.getSigningKeystore(keystore);
    final Certificate cert = ks.getCertificate(getSigningKeyAlias());
    return (cert != null) ? (RSAPublicKey) cert.getPublicKey() : null;
  }

  private String getSigningKeyAlias() {
    final GatewayConfig config = (GatewayConfig) request.getServletContext()
        .getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    final String alias = config.getSigningKeyAlias();
    return (alias == null) ? GatewayConfig.DEFAULT_SIGNING_KEY_ALIAS : alias;
  }

}