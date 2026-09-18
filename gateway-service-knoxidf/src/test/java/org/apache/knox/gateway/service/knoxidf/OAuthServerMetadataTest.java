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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Map;

import jakarta.ws.rs.core.UriInfo;

import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies the RFC 8414 OAuth 2.0 Authorization Server Metadata document: that it carries the
 * required/expected OAuth metadata, that it OMITS OpenID Connect-only claims (so it is a valid
 * OAuth-only document, not a copy of the OIDC discovery document), that both placements (API path and
 * topology root) return the same body, and that its shared fields match what the OIDC document
 * advertises (the two must never drift).
 */
public class OAuthServerMetadataTest {

  private static final String BASE_URI = "https://knox:8443/gateway/knoxidf/";

  private static UriInfo uriInfo() {
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getBaseUri()).andReturn(URI.create(BASE_URI)).anyTimes();
    EasyMock.replay(uriInfo);
    return uriInfo;
  }

  @Test
  public void testAdvertisesRequiredOAuthMetadataAndOmitsOidcOnlyClaims() {
    final Map<String, Object> metadata =
        AuthorizationServerMetadata.buildOAuthMetadata(uriInfo(), null, null);
    final String body = JsonUtils.renderAsJsonString(metadata);

    // RFC 8414 REQUIRED members.
    assertEquals("issuer must match the OIDC discovery document's issuer.",
        BASE_URI + "knoxidf", metadata.get("issuer"));
    assertTrue("response_types_supported is REQUIRED by RFC 8414.",
        body.contains("response_types_supported") && body.contains("\"" + KnoxIDFConstants.CODE + "\""));

    // Endpoints the KnoxIDF authorization server actually serves.
    assertEquals(BASE_URI + AuthorizeResource.RESOURCE_PATH, metadata.get("authorization_endpoint"));
    assertEquals(BASE_URI + TokenResource.RESOURCE_PATH, metadata.get("token_endpoint"));
    assertEquals(BASE_URI + JwksResource.RESOURCE_PATH, metadata.get("jwks_uri"));
    assertEquals(BASE_URI + RegistrationResource.RESOURCE_PATH + "/register", metadata.get("registration_endpoint"));

    // Capabilities -- only what is honored.
    assertTrue("grant_types_supported must advertise authorization_code.",
        body.contains("\"" + KnoxIDFConstants.AUTH_CODE + "\""));
    assertTrue("grant_types_supported must advertise refresh_token.",
        body.contains("\"" + KnoxIDFConstants.REFRESH_TOKEN + "\""));
    assertTrue("grant_types_supported must advertise client_credentials.",
        body.contains("\"" + KnoxIDFConstants.CLIENT_CREDENTIALS + "\""));
    assertTrue("grant_types_supported must advertise the RFC 8693 token-exchange grant type.",
        body.contains("\"" + KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE + "\""));
    assertTrue("token_endpoint_auth_methods_supported must advertise client_secret_post and none.",
        body.contains("token_endpoint_auth_methods_supported")
            && body.contains("client_secret_post") && body.contains("\"none\""));
    assertFalse("Metadata must not advertise client_secret_basic, which is not honored.",
        body.contains("client_secret_basic"));
    assertTrue("code_challenge_methods_supported must advertise S256 only.",
        body.contains("code_challenge_methods_supported")
            && body.contains("\"" + KnoxIDFConstants.PKCE_METHOD_S256 + "\""));
    assertFalse("code_challenge_methods_supported must not advertise plain.",
        body.contains("\"" + KnoxIDFConstants.PKCE_METHOD_PLAIN + "\""));
    assertTrue("scopes_supported must be present.", body.contains("scopes_supported"));

    // OIDC-only claims must be ABSENT from the OAuth-only document.
    assertFalse("userinfo_endpoint is OIDC-only and must not appear.",
        body.contains("userinfo_endpoint"));
    assertFalse("id_token_signing_alg_values_supported is OIDC-only and must not appear.",
        body.contains("id_token_signing_alg_values_supported"));
    assertFalse("subject_types_supported is OIDC-only and must not appear.",
        body.contains("subject_types_supported"));
  }

  @Test
  public void testBothPlacementsReturnIdenticalBody() {
    final OAuthServerMetadataResource apiResource = new OAuthServerMetadataResource();
    final OAuthServerMetadataRootResource rootResource = new OAuthServerMetadataRootResource();

    final String apiBody = String.valueOf(apiResource.getMetadata(uriInfo()).getEntity());
    final String rootBody = String.valueOf(rootResource.getMetadata(uriInfo()).getEntity());

    assertEquals("The topology-root and API-path documents must be byte-identical.", apiBody, rootBody);
  }

  @Test
  public void testSharedFieldsMatchOidcDiscoveryDocument() {
    // The OAuth document's shared fields must equal what DiscoveryResource advertises: the OAuth
    // document is exactly the common subset the OIDC document is built on.
    final String oidcBody = String.valueOf(new DiscoveryResource().getConfig(uriInfo()).getEntity());
    final Map<String, Object> oauth =
        AuthorizationServerMetadata.buildOAuthMetadata(uriInfo(), null, null);

    for (final String field : new String[]{"issuer", "authorization_endpoint", "token_endpoint",
        "jwks_uri", "registration_endpoint"}) {
      assertTrue("OIDC discovery must advertise the same " + field + " value.",
          oidcBody.contains("\"" + field + "\":\"" + oauth.get(field) + "\""));
    }
  }
}
