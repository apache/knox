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
package org.apache.knox.gateway.service.knoxidf;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies that token.exchange.topology.name, authorization.topology.name and
 * client.registration.topology.name each move only their own endpoints in both the OIDC discovery
 * document and the RFC 8414 metadata document, for every set/unset combination, and that an unset
 * or blank parameter keeps the endpoint on the current topology (KNOX-3548).
 */
public class DiscoveryEndpointResolverTest {

  private static final String CURRENT = "knoxidf-sso";
  private static final String TOKEN = "knoxidf-token";
  private static final String AUTHORIZATION = "knoxidf-login";
  private static final String REGISTRATION = "knoxidf-registration";

  @Test
  public void testEachParameterMovesOnlyItsOwnEndpointsInBothDocuments() throws Exception {
    for (int combination = 0; combination < 8; combination++) {
      final String token = (combination & 1) != 0 ? TOKEN : null;
      final String authorization = (combination & 2) != 0 ? AUTHORIZATION : null;
      final String registration = (combination & 4) != 0 ? REGISTRATION : null;
      final ServletContext context = servletContext(CURRENT, token, authorization, registration);
      final String label = "token=" + token + ", authorization=" + authorization + ", registration=" + registration;

      final String expectedToken = token != null ? token : CURRENT;
      final String expectedAuthorization = authorization != null ? authorization : CURRENT;
      final String expectedRegistration = registration != null ? registration : CURRENT;

      final Map<String, Object> oidc = oidc(CURRENT, context);
      assertEndpoints("OIDC " + label, oidc, CURRENT, expectedToken, expectedAuthorization, expectedRegistration);
      // userinfo is served next to the token endpoint and follows token.exchange.topology.name only.
      assertEquals("OIDC " + label + ": userinfo_endpoint",
              base(expectedToken) + UserInfoResource.RESOURCE_PATH, oidc.get("userinfo_endpoint"));

      assertEndpoints("RFC 8414 " + label, oauth(CURRENT, context), CURRENT,
              expectedToken, expectedAuthorization, expectedRegistration);
    }
  }

  @Test
  public void testBlankParametersKeepEndpointsOnCurrentTopology() throws Exception {
    final ServletContext context = servletContext(CURRENT, "", "  ", "");
    assertEndpoints("OIDC", oidc(CURRENT, context), CURRENT, CURRENT, CURRENT, CURRENT);
    assertEndpoints("RFC 8414", oauth(CURRENT, context), CURRENT, CURRENT, CURRENT, CURRENT);
  }

  @Test
  public void testOnlyTheTopologySegmentIsRewritten() throws Exception {
    // A topology named "knoxidf" also matches the "knoxidf/api/v1" resource path; only the base
    // URL's topology segment may be rewritten.
    final ServletContext context = servletContext("knoxidf", TOKEN, AUTHORIZATION, REGISTRATION);
    final Map<String, Object> oidc = oidc("knoxidf", context);
    assertEndpoints("OIDC", oidc, "knoxidf", TOKEN, AUTHORIZATION, REGISTRATION);
    assertEquals(base(TOKEN) + UserInfoResource.RESOURCE_PATH, oidc.get("userinfo_endpoint"));
    assertEndpoints("RFC 8414", oauth("knoxidf", context), "knoxidf", TOKEN, AUTHORIZATION, REGISTRATION);
  }

  private static String base(final String topology) {
    return "https://knox:8443/gateway/" + topology + "/";
  }

  private static UriInfo uriInfo(final String topology) {
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getBaseUri()).andReturn(URI.create(base(topology))).anyTimes();
    EasyMock.replay(uriInfo);
    return uriInfo;
  }

  private static ServletContext servletContext(final String current, final String token,
                                               final String authorization, final String registration) {
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(current).anyTimes();
    EasyMock.expect(context.getInitParameter(KnoxIDFConstants.TOKEN_EXCHANGE_TOPOLOGY_NAME)).andReturn(token).anyTimes();
    EasyMock.expect(context.getInitParameter(KnoxIDFConstants.AUTHORIZATION_TOPOLOGY_NAME))
        .andReturn(authorization).anyTimes();
    EasyMock.expect(context.getInitParameter(KnoxIDFConstants.CLIENT_REGISTRATION_TOPOLOGY_NAME))
        .andReturn(registration).anyTimes();
    EasyMock.replay(context);
    return context;
  }

  private static void inject(final Object resource, final Class<?> declaring, final ServletContext context)
      throws Exception {
    final Field field = declaring.getDeclaredField("servletContext");
    field.setAccessible(true);
    field.set(resource, context);
  }

  private static Map<String, Object> parse(final Object entity) throws Exception {
    return new ObjectMapper().readValue(String.valueOf(entity), new TypeReference<Map<String, Object>>() { });
  }

  private static Map<String, Object> oidc(final String current, final ServletContext context) throws Exception {
    final DiscoveryResource resource = new DiscoveryResource();
    inject(resource, DiscoveryResource.class, context);
    resource.init();
    return parse(resource.getConfig(uriInfo(current)).getEntity());
  }

  private static Map<String, Object> oauth(final String current, final ServletContext context) throws Exception {
    final OAuthServerMetadataResource resource = new OAuthServerMetadataResource();
    inject(resource, OAuthServerMetadataResource.class, context);
    resource.init();
    return parse(resource.getMetadata(uriInfo(current)).getEntity());
  }

  private static void assertEndpoints(final String label, final Map<String, Object> doc, final String current,
                                      final String token, final String authorization, final String registration) {
    assertEquals(label + ": authorization_endpoint",
        base(authorization) + AuthorizeResource.RESOURCE_PATH, doc.get("authorization_endpoint"));
    assertEquals(label + ": token_endpoint", base(token) + TokenResource.RESOURCE_PATH, doc.get("token_endpoint"));
    assertEquals(label + ": registration_endpoint",
        base(registration) + RegistrationResource.RESOURCE_PATH + "/register", doc.get("registration_endpoint"));
    // The issuer and JWKS identify the authorization server serving the document; no parameter moves them.
    assertEquals(label + ": issuer", base(current) + "knoxidf", doc.get("issuer"));
    assertEquals(label + ": jwks_uri", base(current) + JwksResource.RESOURCE_PATH, doc.get("jwks_uri"));
  }
}
