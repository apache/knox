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
package org.apache.knox.gateway.pac4j;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter;
import org.apache.knox.gateway.pac4j.filter.Pac4jIdentityAdapter;
import org.apache.knox.gateway.pac4j.session.KnoxSessionStore;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.test.category.VerifyTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.easymock.EasyMock;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.oidc.client.OidcClient;
import org.testcontainers.containers.GenericContainer;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

@Category(VerifyTest.class)
public class Pac4jProviderOidcClientTest {

    private static final Logger LOGGER = LogManager.getLogger(Pac4jProviderOidcClientTest.class);
    private static final String LOCALHOST = "localhost";
    private static final String HADOOP_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS";
    private static final String KNOXSSO_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/idp/api/v1/websso";
    private static final String PAC4J_CALLBACK_URL = KNOXSSO_SERVICE_URL;
    private static final String ORIGINAL_URL = "originalUrl";
    private static final String CLUSTER_NAME = "knox";
    private static final String PAC4J_PASSWORD = "pwdfortest";
    private static final String CLIENT_CLASS = OidcClient.class.getSimpleName();
    private static final String USERNAME = "JaneUser";
    public static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak";
    public static final String KEYCLOAK_VERSION = "21.1.2";

    private static GenericContainer<?> keycloakContainer;

    @BeforeClass
    public static void setUpClass() {
        try {
            keycloakContainer = new GenericContainer<>(KEYCLOAK_IMAGE + ":"+ KEYCLOAK_VERSION)
            .withExposedPorts(8080)
            .withEnv("KEYCLOAK_ADMIN", "admin")
            .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
            .withCopyFileToContainer(
            org.testcontainers.utility.MountableFile.forClasspathResource("keycloak/realm-export.json"),
            "/opt/keycloak/data/import/realm.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/realms/testrealm/.well-known/openid-configuration").forStatusCode(200));

            keycloakContainer.start();
            assertTrue(keycloakContainer.isRunning());
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (keycloakContainer != null) {
            keycloakContainer.stop();
        }
    }

    @Test
    public void testPac4jOidcLoginWithKeycloak() throws Exception {
        LOGGER.info("Starting Pac4j OIDC login integration test with Keycloak");

        ServletContext context = setupKnoxEnvironment();
        Pac4jFilters filters = setupPac4jFilters(context);
        ClientCookieStore clientCookieStore = new ClientCookieStore();

        LOGGER.info("Step 1: Making unauthenticated request to KnoxSSO");
        MockHttpServletResponse redirectResponse = triggerOidcRedirect(filters, clientCookieStore);
        clientCookieStore.saveCookies(redirectResponse);

        assertEquals("Should redirect to Keycloak", 302, redirectResponse.getStatus());
        String keycloakAuthUrl = redirectResponse.getHeaders().get("Location").get(0);

        verifyOidcRedirectUri(keycloakAuthUrl);
        verifyKnoxSessionCookies(clientCookieStore);

        LOGGER.info("Step 2: Authenticating with Keycloak directly via HTTP client...");
        KeycloakLoginResult loginResult = authenticateWithKeycloak(keycloakAuthUrl, clientCookieStore);
        clientCookieStore.saveCookies(loginResult.cookies);

        assertNotNull("Redirect URL should not be null after successful login", loginResult.redirectUrl);

        LOGGER.info("Step 3: Processing OIDC callback from Keycloak");
        MockHttpServletResponse callbackResponse = processOidcCallback(filters, loginResult.redirectUrl, clientCookieStore);
        clientCookieStore.saveCookies(callbackResponse);

        assertEquals("Should redirect back to the original service URL", 302, callbackResponse.getStatus());
        String finalRedirectUrl = callbackResponse.getHeaders().get("Location").get(0);
        assertEquals(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL, finalRedirectUrl);

        LOGGER.info("Step 4: Executing identity filters with established Knox session");
        MockHttpServletResponse finalResponse = executeKnoxPac4jFilters(filters, clientCookieStore);

        assertEquals(0, finalResponse.getStatus()); // Dispatcher passes through successfully
        assertEquals("User identity should be resolved", USERNAME, filters.adapter.getTestIdentifier());

        LOGGER.info("Identity successfully resolved for user: {}", filters.adapter.getTestIdentifier());
    }

    private ServletContext setupKnoxEnvironment() throws Exception {
        final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD, true))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
        EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
        EasyMock.replay(aliasService);

        final DefaultCryptoService cryptoService = new DefaultCryptoService();
        cryptoService.setAliasService(aliasService);

        final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
        EasyMock.expect(services.getService(ServiceType.CRYPTO_SERVICE)).andReturn(cryptoService);
        EasyMock.expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService);
        EasyMock.replay(services);

        final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
        GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(CLUSTER_NAME);
        EasyMock.replay(context);

        return context;
    }

    private Pac4jFilters setupPac4jFilters(ServletContext context) throws ServletException {
        final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(config.getServletContext()).andReturn(context);
        EasyMock.expect(config.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(PAC4J_CALLBACK_URL).anyTimes();
        EasyMock.expect(config.getInitParameter(Pac4jIdentityAdapter.PAC4J_ID_ATTRIBUTE)).andReturn("customIdAttribute");
        EasyMock.expect(config.getInitParameter("clientName")).andReturn("OidcClient").anyTimes();
        EasyMock.expect(config.getInitParameter("oidc.id")).andReturn("test-client-id").anyTimes();
        EasyMock.expect(config.getInitParameter("oidc.secret")).andReturn("test-client-secret").anyTimes();

        String authServerUrl = String.format(Locale.ROOT, "http://%s:%d", keycloakContainer.getHost(), keycloakContainer.getMappedPort(8080));
        String discoveryUri = authServerUrl + "/realms/testrealm/.well-known/openid-configuration";
        EasyMock.expect(config.getInitParameter("oidc.discoveryUri")).andReturn(discoveryUri).anyTimes();
        EasyMock.expect(config.getInitParameter("oidc.clientAuthenticationMethod")).andReturn("client_secret_basic").anyTimes();
        EasyMock.expect(config.getInitParameter("oidc.preferredJwsAlgorithm")).andReturn("RS256").anyTimes();
        EasyMock.expect(config.getInitParameter("oidc.scope")).andReturn("openid email profile").anyTimes();

        List<String> initParameterNames = Arrays.asList(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL,
        "clientName", "oidc.id", "oidc.secret", "oidc.discoveryUri", "oidc.clientAuthenticationMethod",
        "oidc.preferredJwsAlgorithm", "oidc.scope");
        EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(initParameterNames)).anyTimes();
        EasyMock.replay(config);

        final Pac4jDispatcherFilter dispatcher = new Pac4jDispatcherFilter();
        dispatcher.init(config);

        final Pac4jIdentityAdapter adapter = new Pac4jIdentityAdapter();
        adapter.init(config);

        Pac4jIdentityAdapter.setAuditor(EasyMock.createNiceMock(Auditor.class));
        final AuditService auditService = EasyMock.createNiceMock(AuditService.class);
        EasyMock.expect(auditService.getContext()).andReturn(EasyMock.createNiceMock(AuditContext.class));
        EasyMock.replay(auditService);
        Pac4jIdentityAdapter.setAuditService(auditService);

        return new Pac4jFilters(dispatcher, adapter);
    }

    private MockHttpServletResponse triggerOidcRedirect(Pac4jFilters filters, ClientCookieStore clientCookieStore) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE, CLUSTER_NAME);
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setCookies(clientCookieStore.getCookies().toArray(new Cookie[0]));
        request.setServerName(LOCALHOST);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);
        filters.dispatcher.doFilter(request, response, filterChain);

        return response;
    }

    private KeycloakLoginResult authenticateWithKeycloak(String authUrl, ClientCookieStore clientCookieStore) throws Exception {
        BasicCookieStore apacheCookieStore = getBasicCookieStore(clientCookieStore);

        String redirectUrlAfterLogin;
        List<Cookie> setCookiesAfterLogin = new ArrayList<>();

        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultCookieStore(apacheCookieStore).build()) {
            String loginPageHtml;
            try (CloseableHttpResponse loginPageResponse = httpClient.execute(new HttpGet(authUrl))) {
                loginPageHtml = EntityUtils.toString(loginPageResponse.getEntity());
            }

            Document doc = Jsoup.parse(loginPageHtml);
            String postUrl = doc.select("#kc-form-login").attr("action");

            HttpPost httpPost = new HttpPost(postUrl);
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("username", "user"));
            params.add(new BasicNameValuePair("password", "user-password"));
            params.add(new BasicNameValuePair("credentialId", ""));
            httpPost.setEntity(new UrlEncodedFormEntity(params));

            try (CloseableHttpResponse loginResponse = httpClient.execute(httpPost)) {
                int statusCode = loginResponse.getStatusLine().getStatusCode();
                if (statusCode == 302) {
                    redirectUrlAfterLogin = loginResponse.getFirstHeader("Location").getValue();
                    Header[] headers = loginResponse.getHeaders("Set-Cookie");
                    if (headers != null) {
                        for (Header header : headers) {
                            setCookiesAfterLogin.add(ClientCookieStore.parseCookieString(header.getValue()));
                        }
                    }
                } else {
                    throw new RuntimeException("Keycloak login failed with status: " + statusCode);
                }
                EntityUtils.consume(loginResponse.getEntity());
            }
        }

        return new KeycloakLoginResult(redirectUrlAfterLogin, setCookiesAfterLogin);
    }

    private static BasicCookieStore getBasicCookieStore(ClientCookieStore clientCookieStore) {
        BasicCookieStore basicCookieStore = new BasicCookieStore();

        for (Cookie servletCookie : clientCookieStore.getCookies()) {
            BasicClientCookie clientCookie = new BasicClientCookie(servletCookie.getName(), servletCookie.getValue());
            clientCookie.setDomain(servletCookie.getDomain() != null ? servletCookie.getDomain() : LOCALHOST);
            clientCookie.setPath(servletCookie.getPath() != null ? servletCookie.getPath() : "/");
            basicCookieStore.addCookie(clientCookie);
        }
        return basicCookieStore;
    }

    private MockHttpServletResponse processOidcCallback(Pac4jFilters filters, String callbackUrl, ClientCookieStore clientCookieStore) throws Exception {
        assertTrue("Expected Keycloak to redirect back to Knox, but got: " + callbackUrl, callbackUrl.startsWith(KNOXSSO_SERVICE_URL));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE, CLUSTER_NAME);
        request.setCookies(clientCookieStore.getCookies().toArray(new Cookie[0]));
        request.setRequestURL(callbackUrl);
        request.setServerName(LOCALHOST);

        String state = getQueryParameter(callbackUrl, "state");
        String code = getQueryParameter(callbackUrl, "code");
        String sessionState = getQueryParameter(callbackUrl, "session_state");

        request.addParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER, "true");
        request.addParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, CLIENT_CLASS);
        request.addParameter("state", state);
        request.addParameter("session_state", sessionState);
        request.addParameter("code", code);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);
        filters.dispatcher.doFilter(request, response, filterChain);

        return response;
    }

    private MockHttpServletResponse executeKnoxPac4jFilters(Pac4jFilters filters, ClientCookieStore clientCookieStore) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE, CLUSTER_NAME);
        request.setCookies(clientCookieStore.getCookies().toArray(new Cookie[0]));
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setServerName(LOCALHOST);

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);

        filters.dispatcher.doFilter(request, response, filterChain);
        filters.adapter.doFilter(request, response, filterChain);

        return response;
    }

    private void verifyOidcRedirectUri(String locationHeader) throws Exception {
        String redirectUriParam = getQueryParameter(locationHeader, "redirect_uri");

        String redirectUri = URLDecoder.decode(redirectUriParam, StandardCharsets.UTF_8.name());
        String expectedRedirectUri = PAC4J_CALLBACK_URL + "?"
        + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&"
        + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS;
        assertEquals(expectedRedirectUri, redirectUri);
    }

    private void verifyKnoxSessionCookies(ClientCookieStore clientCookieStore) {
        boolean hasRequestedUrlCookie = clientCookieStore.getCookies().stream()
        .anyMatch(c -> c.getName().equals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL));
        assertTrue("Must contain PAC4J requested URL cookie", hasRequestedUrlCookie);

        long csrfTokenCount = clientCookieStore.getCookies().stream()
        .filter(c -> c.getName().equals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.CSRF_TOKEN)).count();
        assertEquals("Must contain exactly one PAC4J CSRF token cookie", 1, csrfTokenCount);
    }

    private String getQueryParameter(String url, String paramName) throws Exception {
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(url), StandardCharsets.UTF_8);
        return params.stream()
        .filter(p -> paramName.equals(p.getName()))
        .findFirst()
        .map(NameValuePair::getValue)
        .orElse(null);
    }

    private static class Pac4jFilters {
        final Pac4jDispatcherFilter dispatcher;
        final Pac4jIdentityAdapter adapter;

        Pac4jFilters(Pac4jDispatcherFilter dispatcher, Pac4jIdentityAdapter adapter) {
            this.dispatcher = dispatcher;
            this.adapter = adapter;
        }
    }

    private static class KeycloakLoginResult {
        final String redirectUrl;
        final List<Cookie> cookies;

        KeycloakLoginResult(String redirectUrl, List<Cookie> cookies) {
            this.redirectUrl = redirectUrl;
            this.cookies = cookies;
        }
    }

    /**
     * Stores and updates cookies across requests to maintain state.
     */
    private static class ClientCookieStore {
        private final Map<String, Cookie> cookies = new HashMap<>();

        public List<Cookie> getCookies() {
            return new ArrayList<>(cookies.values());
        }

        public void saveCookies(MockHttpServletResponse response) {
            if (response.getCookies() != null) {
                for (Cookie c : response.getCookies()) {
                    cookies.put(c.getName(), c);
                }
            }
            List<String> headers = response.getHeaders().get("Set-Cookie");
            if (headers != null) {
                for (String header : headers) {
                    Cookie c = parseCookieString(header);
                    cookies.put(c.getName(), c);
                }
            }
        }

        public void saveCookies(List<Cookie> newCookies) {
            if (newCookies != null) {
                for (Cookie c : newCookies) {
                    cookies.put(c.getName(), c);
                }
            }
        }

        public static Cookie parseCookieString(String setCookieHeader) {
            String[] cookieParts = setCookieHeader.split(";");
            String[] nameValuePairs = cookieParts[0].trim().split("=", 2);
            return new Cookie(nameValuePairs[0].trim(), nameValuePairs[1].trim());
        }
    }

}
