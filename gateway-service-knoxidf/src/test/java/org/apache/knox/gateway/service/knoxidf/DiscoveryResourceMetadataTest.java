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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URI;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies the discovery document carries required/expected OIDC provider metadata.
 * subject_types_supported is REQUIRED by OpenID Connect Discovery 1.0; a strict client or
 * conformance validator rejects a document that omits it. registration_endpoint must point at the
 * dynamic client registration resource that KnoxIDF actually serves.
 */
public class DiscoveryResourceMetadataTest {

  @Test
  public void testAdvertisesSubjectTypesAndRegistrationEndpoint() {
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getBaseUri()).andReturn(URI.create("https://knox:8443/gateway/knoxidf/")).anyTimes();
    EasyMock.replay(uriInfo);

    final Response response = new DiscoveryResource().getConfig(uriInfo);
    final String body = String.valueOf(response.getEntity());

    // subject_types_supported is REQUIRED; Knox uses a shared (non-pairwise) subject -> "public".
    assertTrue("subject_types_supported must be present (REQUIRED by OIDC Discovery).",
        body.contains("subject_types_supported"));
    assertTrue("subject_types_supported must advertise 'public'.",
        body.contains("\"public\""));

    // registration_endpoint must resolve to the dynamic client registration resource.
    assertTrue("registration_endpoint must point at the /client registration resource.",
        body.contains("registration_endpoint") && body.contains(RegistrationResource.RESOURCE_PATH));

    // The token endpoint authenticates clients via body params only: client_secret_post + none (PKCE).
    assertTrue("token_endpoint_auth_methods_supported must advertise client_secret_post and none.",
        body.contains("token_endpoint_auth_methods_supported")
            && body.contains("client_secret_post") && body.contains("\"none\""));
    // It must NOT claim HTTP Basic client auth, which the token endpoint does not read.
    assertFalse("Discovery must not advertise client_secret_basic, which is not honored.",
        body.contains("client_secret_basic"));

    // CIMD is not implemented, so it must be advertised explicitly as false (never true).
    assertTrue("client_id_metadata_document_supported must be present and false.",
        body.contains("\"client_id_metadata_document_supported\":false"));

    assertTrue("grant_types_supported must advertise authorization_code.",
        body.contains("grant_types_supported") && body.contains("\"" + KnoxIDFConstants.AUTH_CODE + "\""));
    assertTrue("grant_types_supported must advertise refresh_token.",
        body.contains("\"" + KnoxIDFConstants.REFRESH_TOKEN + "\""));
    assertTrue("grant_types_supported must advertise client_credentials.",
        body.contains("\"" + KnoxIDFConstants.CLIENT_CREDENTIALS + "\""));
    assertTrue("grant_types_supported must advertise the RFC 8693 token-exchange grant type.",
        body.contains("\"" + KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE + "\""));
  }
}
