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
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.service.knoxtoken.ClientCredentialsResource;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.glassfish.jersey.process.internal.RequestScoped;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.BASE_RESOURCE_PATH;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_REGISTRATION_ALLOWED_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_REGISTRATION_ANONYMOUS_ALLOWED;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_REGISTRATION_CUSTOM_LOOPBACK_HOSTS;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.DEFAULT_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.OIDC_STANDARD_SCOPES;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.error;

@Path(RegistrationResource.RESOURCE_PATH)
@RequestScoped //this is important because redirectUris/allowedScopes are part of the state of this class
public class RegistrationResource extends ClientCredentialsResource {

    static final String RESOURCE_PATH = BASE_RESOURCE_PATH + "/client";
    private static final String ANONYMOUS_PRINCIPAL = "anonymous";
    static final Set<String> DEFAULT_LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private List<String> redirectUris;
    private List<String> allowedScopes;
    boolean anonymousRegistrationAllowed;
    Set<String> loopbackHosts;
    Set<String> registerableScopes;

    @Context
    private ServletContext servletContext;

    @Override
    public String getPrefix() {
        return "knoxidf.";
    }

    @PostConstruct
    @Override
    public void init() throws ServletException, AliasServiceException, ServiceLifecycleException, KeyLengthException {
        super.init();
        // Secure by default: unless the deployment explicitly opts in, an anonymous caller cannot
        // register a client even when the topology wires this endpoint as 'anon'.
        this.anonymousRegistrationAllowed = Boolean.parseBoolean(servletContext.getInitParameter(CLIENT_REGISTRATION_ANONYMOUS_ALLOWED));
        this.loopbackHosts = parseLoopbackHosts(servletContext.getInitParameter(CLIENT_REGISTRATION_CUSTOM_LOOPBACK_HOSTS));
        this.registerableScopes = parseRegisterableScopes(servletContext.getInitParameter(CLIENT_REGISTRATION_ALLOWED_SCOPES));
    }

    // Build the set of scopes a client is permitted to register: the operator-configured whitelist
    // (comma-separated, trimmed, blanks dropped) or, when unset/blank, the OIDC-standard scope set.
    // 'openid' is always registerable regardless of the configured value.
    static Set<String> parseRegisterableScopes(String configured) {
        if (StringUtils.isBlank(configured)) {
            return OIDC_STANDARD_SCOPES;
        }
        final Set<String> scopes = new HashSet<>();
        for (String s : configured.split(",")) {
            final String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                scopes.add(trimmed);
            }
        }
        scopes.add("openid");
        return scopes;
    }

    // Build the loopback-host set: the hard-coded defaults plus any admin-configured extra hosts from the
    // comma-separated config (trimmed, lowercased, blanks dropped). Null/blank config => defaults only.
    static Set<String> parseLoopbackHosts(String customLoopbackHosts) {
        if (StringUtils.isBlank(customLoopbackHosts)) {
            return DEFAULT_LOOPBACK_HOSTS;
        }
        final Set<String> hosts = new HashSet<>(DEFAULT_LOOPBACK_HOSTS);
        for (String h : customLoopbackHosts.split(",")) {
            final String trimmed = h.trim();
            if (!trimmed.isEmpty()) {
                hosts.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return hosts;
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
        // Audit the outcome of every dynamic client-registration attempt exactly once, recording the
        // caller principal and the reason for a rejection. No secret (the minted client_secret) is
        // ever logged — only that a client was registered.
        final String caller = KnoxIDFAudit.subjectLabel(SubjectUtils.getCurrentEffectivePrincipalName());
        String outcome = ActionOutcome.FAILURE;
        String detail = "reason=unknown";
        try {
            if (anonymousRegistrationDenied()) {
                detail = "reason=anonymous_denied";
                return error("access_denied", "Anonymous client registration is disabled. Set '"
                        + CLIENT_REGISTRATION_ANONYMOUS_ALLOWED + "' to true in the KNOXIDF service configuration to enable it.");
            }
            if (StringUtils.isBlank(redirectUris)) {
                detail = "reason=missing_redirect_uris";
                return error("invalid_request", "redirect_uris must be provided");
            }
            this.redirectUris = Arrays.asList(redirectUris.split(","));
            final Response redirectUriVerificationResponse = verifyRedirectUris();
            if (redirectUriVerificationResponse != null) {
                detail = "reason=invalid_redirect_uris";
                return redirectUriVerificationResponse;
            }

            if (StringUtils.isBlank(allowedScopes)) {
                // No scopes requested: grant the built-in defaults, bounded by the server-side
                // whitelist so a narrower operator policy is honored even for the default case.
                this.allowedScopes = DEFAULT_SCOPES.stream()
                        .filter(registerableScopes::contains)
                        .collect(Collectors.toList());
            } else {
                final List<String> requestedScopes = Arrays.asList(allowedScopes.split(","));
                if (!requestedScopes.contains("openid")) {
                    detail = "reason=invalid_scope";
                    return error("invalid_request", "allowed_scopes must include 'openid'");
                }
                // Server-side whitelist: a client cannot self-assign a scope outside the registerable
                // set, so it cannot mint tokens carrying a privileged scope a downstream service trusts.
                final Optional<String> disallowedScope = requestedScopes.stream()
                        .map(String::trim)
                        .filter(scope -> !scope.isEmpty())
                        .filter(scope -> !registerableScopes.contains(scope))
                        .findFirst();
                if (disallowedScope.isPresent()) {
                    detail = "reason=scope_not_registerable";
                    return error("invalid_scope", "Scope '" + disallowedScope.get() + "' is not permitted for registration");
                }
                this.allowedScopes = requestedScopes;
            }
            final Response response = super.doPost();
            outcome = ActionOutcome.SUCCESS;
            detail = "reason=client_registered";
            return response;
        } finally {
            KnoxIDFAudit.audit(Action.AUTHENTICATION, caller, ResourceType.PRINCIPAL, outcome,
                    "event=client_registration " + detail);
        }
    }

    /**
     * @return {@code true} when the request must be rejected because an anonymous caller is
     * attempting to register a client while open registration has not been explicitly enabled.
     */
    boolean anonymousRegistrationDenied() {
        return !anonymousRegistrationAllowed && isAnonymousCaller();
    }

    private boolean isAnonymousCaller() {
        return ANONYMOUS_PRINCIPAL.equalsIgnoreCase(SubjectUtils.getCurrentEffectivePrincipalName());
    }

    private Response verifyRedirectUris() {
        return verifyRedirectUris(redirectUris, loopbackHosts);
    }

    // Package-private and list-parameterized so the redirect-URI policy (https-only except loopback,
    // no wildcard host, restricted path/query/fragment wildcards) is unit-testable in isolation.
    static Response verifyRedirectUris(List<String> redirectUris) {
        return verifyRedirectUris(redirectUris, DEFAULT_LOOPBACK_HOSTS);
    }

    // loopbackHosts: normalized (lowercase) hosts allowed to use a plain-HTTP redirect_uri.
    static Response verifyRedirectUris(List<String> redirectUris, Set<String> loopbackHosts) {
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

            // Host check (no wildcard allowed)
            if (uri.getHost() == null || uri.getHost().contains("*")) {
                return error("invalid_request", "Wildcard not allowed in host: " + uriStr);
            }

            // Scheme check: require HTTPS per RFC 8252, allowing plain HTTP only for loopback
            // (localhost / 127.0.0.1 / ::1) native-app dev. Any other http:// redirect is rejected.
            final String scheme = uri.getScheme();
            final boolean https = "https".equalsIgnoreCase(scheme);
            final boolean loopbackHttp = "http".equalsIgnoreCase(scheme) && isLoopbackHost(uri.getHost(), loopbackHosts);
            if (!https && !loopbackHttp) {
                return error("invalid_request", "Redirect URI must use HTTPS (plain HTTP allowed only for localhost): " + uriStr);
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

    private static boolean isLoopbackHost(String host, Set<String> loopbackHosts) {
        if (host == null) {
            return false;
        }
        // Strip brackets from an IPv6 literal (e.g. [::1]).
        final String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        // Exact, case-insensitive match against the single loopback-host set (defaults + configured extras).
        // No sub/parent-domain widening: only hosts explicitly listed get the plain-HTTP exception.
        return loopbackHosts.contains(h.toLowerCase(Locale.ROOT));
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
