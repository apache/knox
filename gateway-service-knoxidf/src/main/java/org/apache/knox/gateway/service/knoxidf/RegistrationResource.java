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

import com.nimbusds.jose.KeyLengthException;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.service.knoxtoken.ClientCredentialsResource;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.annotation.PostConstruct;
import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESORCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.DEFAULT_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;

@Path(RegistrationResource.RESOURCE_PATH)
@RequestScoped //this is important because redirectUris/allowedScopes are part of the state of this class
public class RegistrationResource extends ClientCredentialsResource {

    static final String RESOURCE_PATH = BASE_RESORCE_PATH + "/client";
    private List<String> redirectUris;
    private List<String> allowedScopes;

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
    }

    @Override
    @GET
    public Response doGet() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    @POST
    public Response doPost() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Path("/register")
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response registerClient(@FormParam("redirect_uris") String redirectUris,
                                   @FormParam("allowed_scopes") String allowedScopes) {
        this.redirectUris = Arrays.asList(redirectUris.split(","));
        final Response redirectUriVerificationResponse = verifyRedirectUris();
        if (redirectUriVerificationResponse != null) {
            return redirectUriVerificationResponse;
        }

        if (StringUtils.isBlank(allowedScopes)) {
            this.allowedScopes = new ArrayList<>(DEFAULT_SCOPES);
        } else {
            this.allowedScopes = Arrays.asList(allowedScopes.split(","));
            if (!this.allowedScopes.contains("openid")) {
                return error("invalid_request", "allowed_scopes must include 'openid'");
            }
        }
        return super.doPost();
    }

    private Response verifyRedirectUris() {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return error("invalid_request", "redirect_uris must be provided");
        }

        for (String uriStr : redirectUris) {
            URI uri;
            try {
                uri = new URI(uriStr);
            } catch (URISyntaxException e) {
                return error("invalid_request", "Invalid redirect URI: " + uriStr);
            }

            // Scheme check
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) {
                return error("invalid_request", "Redirect URI must use HTTPS or HTTP as scheme: " + uriStr);
            }

            // Host check (no wildcard allowed)
            if (uri.getHost() == null || uri.getHost().contains("*")) {
                return error("invalid_request", "Wildcard not allowed in host: " + uriStr);
            }

            // Path wildcard check
            String path = uri.getPath();
            if (path != null && path.contains("*") && !path.endsWith("*")) {
                return error("invalid_request", "Wildcard '*' only allowed at end of path: " + uriStr);
            }

            // Query/fragment check
            if ((uri.getQuery() != null && uri.getQuery().contains("*")) ||
                    (uri.getFragment() != null && uri.getFragment().contains("*"))) {
                return error("invalid_request", "Wildcard '*' not allowed in query or fragment: " + uriStr);
            }
        }
        return null;
    }

    @Override
    protected void addArbitraryTokenMetadata(TokenMetadata tokenMetadata) {
        tokenMetadata.add("redirect_uris", getRedirectUris());
        tokenMetadata.add("allowed_scopes", getAllowedScopes().replaceAll(",", " "));
        super.addArbitraryTokenMetadata(tokenMetadata);
    }

    @Override
    protected void decorateResponseMap(Map<String, Object> responseMap) {
        responseMap.put("redirect_uris", getRedirectUris());
        responseMap.put("allowed_scopes", getAllowedScopes());
    }

    private String getRedirectUris() {
        return String.join(",", redirectUris);
    }

    private String getAllowedScopes() {
        return String.join(",", allowedScopes);
    }
}
