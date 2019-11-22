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

import java.security.KeyStoreException;
import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.util.JsonUtils;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Singleton
@Path(TokenResource.RESOURCE_PATH)
public class TokenResource {
  private static final String EXPIRES_IN = "expires_in";
  private static final String TOKEN_TYPE = "token_type";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String TARGET_URL = "target_url";
  private static final String ENDPOINT_PUBLIC_CERT = "endpoint_public_cert";
  private static final String BEARER = "Bearer";
  private static final String TOKEN_TTL_PARAM = "knox.token.ttl";
  private static final String TOKEN_AUDIENCES_PARAM = "knox.token.audiences";
  private static final String TOKEN_TARGET_URL = "knox.token.target.url";
  private static final String TOKEN_CLIENT_DATA = "knox.token.client.data";
  private static final String TOKEN_CLIENT_CERT_REQUIRED = "knox.token.client.cert.required";
  private static final String TOKEN_ALLOWED_PRINCIPALS = "knox.token.allowed.principals";
  private static final String TOKEN_SIG_ALG = "knox.token.sigalg";
  private static final String TOKEN_EXP_RENEWAL_INTERVAL = "knox.token.exp.renew-interval";
  private static final String TOKEN_EXP_RENEWAL_MAX_LIFETIME = "knox.token.exp.max-lifetime";
  private static final String TOKEN_RENEWER_WHITELIST = "knox.token.renewer.whitelist";
  private static final long TOKEN_TTL_DEFAULT = 30000L;
  static final String RESOURCE_PATH = "knoxtoken/api/v1/token";
  static final String RENEW_PATH = "/renew";
  static final String REVOKE_PATH = "/revoke";
  private static final String TARGET_ENDPOINT_PULIC_CERT_PEM = "knox.token.target.endpoint.cert.pem";
  private static TokenServiceMessages log = MessagesFactory.get(TokenServiceMessages.class);
  private long tokenTTL = TOKEN_TTL_DEFAULT;
  private List<String> targetAudiences = new ArrayList<>();
  private String tokenTargetUrl;
  private Map<String, Object> tokenClientDataMap;
  private List<String> allowedDNs = new ArrayList<>();
  private boolean clientCertRequired;
  private String signatureAlgorithm = "RS256";
  private String endpointPublicCert;

  // Optional token store service
  private TokenStateService tokenStateService;

  private Optional<Long> renewInterval = Optional.empty();

  private Optional<Long> maxTokenLifetime = Optional.empty();

  private List<String> allowedRenewers;

  @Context
  HttpServletRequest request;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {

    String audiences = context.getInitParameter(TOKEN_AUDIENCES_PARAM);
    if (audiences != null) {
      String[] auds = audiences.split(",");
      for (String aud : auds) {
        targetAudiences.add(aud.trim());
      }
    }

    String clientCert = context.getInitParameter(TOKEN_CLIENT_CERT_REQUIRED);
    clientCertRequired = "true".equals(clientCert);

    String principals = context.getInitParameter(TOKEN_ALLOWED_PRINCIPALS);
    if (principals != null) {
      String[] dns = principals.split(";");
      for (String dn : dns) {
        allowedDNs.add(dn.replaceAll("\\s+", ""));
      }
    }

    String ttl = context.getInitParameter(TOKEN_TTL_PARAM);
    if (ttl != null) {
      try {
        tokenTTL = Long.parseLong(ttl);
        if (tokenTTL < -1 || (tokenTTL + System.currentTimeMillis() < 0)) {
          log.invalidTokenTTLEncountered(ttl);
          tokenTTL = TOKEN_TTL_DEFAULT;
        }
      } catch (NumberFormatException nfe) {
        log.invalidTokenTTLEncountered(ttl);
      }
    }

    tokenTargetUrl = context.getInitParameter(TOKEN_TARGET_URL);

    String clientData = context.getInitParameter(TOKEN_CLIENT_DATA);
    if (clientData != null) {
      tokenClientDataMap = new HashMap<>();
      String[] tokenClientData = clientData.split(",");
      addClientDataToMap(tokenClientData, tokenClientDataMap);
    }

    String sigAlg = context.getInitParameter(TOKEN_SIG_ALG);
    if (sigAlg != null) {
      signatureAlgorithm = sigAlg;
    }

    String targetEndpointPublicCert = context.getInitParameter(TARGET_ENDPOINT_PULIC_CERT_PEM);
    if (targetEndpointPublicCert != null) {
      endpointPublicCert = targetEndpointPublicCert;
    }

    // If server-managed token expiration is configured, set the token store service
    if (Boolean.valueOf(context.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED))) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);

      String renewIntervalValue = context.getInitParameter(TOKEN_EXP_RENEWAL_INTERVAL);
      if (renewIntervalValue != null && !renewIntervalValue.isEmpty()) {
        try {
          renewInterval = Optional.of(Long.parseLong(renewIntervalValue));
        } catch (NumberFormatException e) {
          log.invalidConfigValue(TOKEN_EXP_RENEWAL_INTERVAL, renewIntervalValue, e);
        }
      }

      String maxLifetimeValue = context.getInitParameter(TOKEN_EXP_RENEWAL_MAX_LIFETIME);
      if (maxLifetimeValue != null && !maxLifetimeValue.isEmpty()) {
        try {
          maxTokenLifetime = Optional.of(Long.parseLong(maxLifetimeValue));
        } catch (NumberFormatException e) {
          log.invalidConfigValue(TOKEN_EXP_RENEWAL_MAX_LIFETIME, maxLifetimeValue, e);
        }
      }

      allowedRenewers = new ArrayList<>();
      String renewerList = context.getInitParameter(TOKEN_RENEWER_WHITELIST);
      if (renewerList != null && !renewerList.isEmpty()) {
        for (String renewer : renewerList.split(",")) {
          allowedRenewers.add(renewer.trim());
        }
      }
    }
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doGet() {
    return getAuthenticationToken();
  }

  @POST
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doPost() {
    return getAuthenticationToken();
  }

  @POST
  @Path(RENEW_PATH)
  @Produces({APPLICATION_JSON})
  public Response renew(String token) {
    Response resp;

    long expiration = 0;
    String  error   = "";

    if (tokenStateService == null) {
      error = "Token renewal support is not configured";
    } else {
      String renewer = SubjectUtils.getCurrentEffectivePrincipalName();
      if (allowedRenewers.contains(renewer)) {
        try {
          // If renewal fails, it should be an exception
          expiration = tokenStateService.renewToken(token,
                                                    renewInterval.orElse(tokenStateService.getDefaultRenewInterval()));
        } catch (Exception e) {
          error = e.getMessage();
        }
      } else {
        error = "Caller (" + renewer + ") not authorized to renew tokens.";
      }
    }

    if(error.isEmpty()) {
      resp =  Response.status(Response.Status.OK)
                      .entity("{\n  \"renewed\": \"true\",\n  \"expires\": \"" + expiration + "\"\n}\n")
                      .build();
    } else {
      resp = Response.status(Response.Status.BAD_REQUEST)
                     .entity("{\n  \"renewed\": \"false\",\n  \"error\": \"" + error + "\"\n}\n")
                     .build();
    }

    return resp;
  }

  @POST
  @Path(REVOKE_PATH)
  @Produces({APPLICATION_JSON})
  public Response revoke(String token) {
    Response resp;

    String error = "";

    if (tokenStateService == null) {
      error = "Token revocation support is not configured";
    } else {
      String renewer = SubjectUtils.getCurrentEffectivePrincipalName();
      if (allowedRenewers.contains(renewer)) {
        try {
          tokenStateService.revokeToken(token);
        } catch (IllegalArgumentException e) {
          error = e.getMessage();
        }
      } else {
        error = "Caller (" + renewer + ") not authorized to revoke tokens.";
      }
    }

    if(error.isEmpty()) {
      resp =  Response.status(Response.Status.OK)
                      .entity("{\n  \"revoked\": \"true\"\n}\n")
                      .build();
    } else {
      resp = Response.status(Response.Status.BAD_REQUEST)
                     .entity("{\n  \"revoked\": \"false\",\n  \"error\": \"" + error + "\"\n}\n")
                     .build();
    }

    return resp;
  }

  private X509Certificate extractCertificate(HttpServletRequest req) {
    X509Certificate[] certs = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
    if (null != certs && certs.length > 0) {
      return certs[0];
    }
    return null;
  }

  private Response getAuthenticationToken() {
    if (clientCertRequired) {
      X509Certificate cert = extractCertificate(request);
      if (cert != null) {
        if (!allowedDNs.contains(cert.getSubjectDN().getName().replaceAll("\\s+", ""))) {
          return Response.status(403).entity("{ \"Unable to get token - untrusted client cert.\" }").build();
        }
      } else {
        return Response.status(403).entity("{ \"Unable to get token - client cert required.\" }").build();
      }
    }
    GatewayServices services = (GatewayServices) request.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);

    JWTokenAuthority ts = services.getService(ServiceType.TOKEN_SERVICE);
    Principal p = request.getUserPrincipal();
    long expires = getExpiry();

    if (endpointPublicCert == null) {
      // acquire PEM for gateway identity of this gateway instance
      KeystoreService ks = services.getService(ServiceType.KEYSTORE_SERVICE);
      if (ks != null) {
        try {
          Certificate cert = ks.getCertificateForGateway();
          byte[] bytes = cert.getEncoded();
          //Base64 encoder = new Base64(76, "\n".getBytes("ASCII"));
          endpointPublicCert = Base64.encodeBase64String(bytes);
        } catch (KeyStoreException | KeystoreServiceException | CertificateEncodingException e) {
          // assuming that certs will be properly provisioned across all clients
          log.unableToAcquireCertForEndpointClients(e);
        }
      }
    }

    try {
      JWT token;
      if (targetAudiences.isEmpty()) {
        token = ts.issueToken(p, signatureAlgorithm, expires);
      } else {
        token = ts.issueToken(p, targetAudiences, signatureAlgorithm, expires);
      }

      if (token != null) {
        String accessToken = token.toString();
        HashMap<String, Object> map = new HashMap<>();
        map.put(ACCESS_TOKEN, accessToken);
        map.put(TOKEN_TYPE, BEARER);
        map.put(EXPIRES_IN, expires);
        if (tokenTargetUrl != null) {
          map.put(TARGET_URL, tokenTargetUrl);
        }
        if (tokenClientDataMap != null) {
          map.putAll(tokenClientDataMap);
        }
        if (endpointPublicCert != null) {
          map.put(ENDPOINT_PUBLIC_CERT, endpointPublicCert);
        }

        String jsonResponse = JsonUtils.renderAsJsonString(map);

        // Optional token store service persistence
        if (tokenStateService != null) {
          tokenStateService.addToken(accessToken,
                                     System.currentTimeMillis(),
                                     expires,
                                     maxTokenLifetime.orElse(tokenStateService.getDefaultMaxLifetimeDuration()));
        }

        return Response.ok().entity(jsonResponse).build();
      } else {
        return Response.serverError().build();
      }
    } catch (TokenServiceException e) {
      log.unableToIssueToken(e);
    }
    return Response.ok().entity("{ \"Unable to acquire token.\" }").build();
  }

  void addClientDataToMap(String[] tokenClientData,
      Map<String,Object> map) {
    String[] kv;
    for (String tokenClientDatum : tokenClientData) {
      kv = tokenClientDatum.split("=");
      if (kv.length == 2) {
        map.put(kv[0], kv[1]);
      }
    }
  }

  private long getExpiry() {
    long expiry;
    if (tokenTTL == -1) {
      expiry = -1;
    } else {
      expiry = System.currentTimeMillis() + tokenTTL;
    }
    return expiry;
  }
}
