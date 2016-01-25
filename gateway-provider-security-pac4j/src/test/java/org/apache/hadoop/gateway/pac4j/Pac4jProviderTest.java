/**
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
package org.apache.hadoop.gateway.pac4j;

import org.apache.hadoop.gateway.audit.api.AuditContext;
import org.apache.hadoop.gateway.audit.api.AuditService;
import org.apache.hadoop.gateway.audit.api.Auditor;
import org.apache.hadoop.gateway.pac4j.filter.Pac4jDispatcherFilter;
import org.apache.hadoop.gateway.pac4j.filter.Pac4jIdentityAdapter;
import org.apache.hadoop.gateway.pac4j.session.KnoxSessionStore;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.impl.DefaultCryptoService;
import org.junit.Test;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;

import javax.servlet.*;
import javax.servlet.http.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

/**
 * This class simulates a full authentication process using pac4j.
 */
public class Pac4jProviderTest {

    private final static String LOCALHOST = "127.0.0.1";
    private final static String HADOOP_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/sandox/webhdfs/v1/tmp?op=LISTSTATUS";
    private final static String KNOXSSO_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/idp/api/v1/websso";
    private final static String PAC4J_CALLBACK_URL = KNOXSSO_SERVICE_URL;
    private final static String ORIGINAL_URL = "originalUrl";
    private final static String CLUSTER_NAME = "knox";
    private final static String PAC4J_PASSWORD = "pwdfortest";
    private final static String CLIENT_CLASS = IndirectBasicAuthClient.class.getSimpleName();
    private final static String USERNAME = "jleleu";

    @Test
    public void test() throws Exception {
        final AliasService aliasService = mock(AliasService.class);
        when(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD, true)).thenReturn(PAC4J_PASSWORD.toCharArray());
        when(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, KnoxSessionStore.PAC4J_PASSWORD)).thenReturn(PAC4J_PASSWORD.toCharArray());

        final DefaultCryptoService cryptoService = new DefaultCryptoService();
        cryptoService.setAliasService(aliasService);

        final GatewayServices services = mock(GatewayServices.class);
        when(services.getService(GatewayServices.CRYPTO_SERVICE)).thenReturn(cryptoService);
        when(services.getService(GatewayServices.ALIAS_SERVICE)).thenReturn(aliasService);

        final ServletContext context = mock(ServletContext.class);
        when(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).thenReturn(services);
        when(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).thenReturn(CLUSTER_NAME);

        final FilterConfig config = mock(FilterConfig.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).thenReturn(PAC4J_CALLBACK_URL);
        when(config.getInitParameter(Pac4jConstants.CLIENT_NAME)).thenReturn(Pac4jDispatcherFilter.TEST_BASIC_AUTH);

        final Pac4jDispatcherFilter dispatcher = new Pac4jDispatcherFilter();
        dispatcher.init(config);
        final Pac4jIdentityAdapter adapter = new Pac4jIdentityAdapter();
        adapter.init(config);
        adapter.setAuditor(mock(Auditor.class));
        final AuditService auditService = mock(AuditService.class);
        when(auditService.getContext()).thenReturn(mock(AuditContext.class));
        adapter.setAuditService(auditService);

        // step 1: call the KnoxSSO service with an original url pointing to an Hadoop service (redirected by the SSOCookieProvider)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setCookies(new Cookie[0]);
        request.setServerName(LOCALHOST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the idp topology
        assertEquals(302, response.getStatus());
        assertEquals(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Clients.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS, response.getHeaders().get("Location"));
        // we should have one cookie for the saved requested url
        List<Cookie> cookies = response.getCookies();
        assertEquals(1, cookies.size());
        final Cookie requestedUrlCookie = cookies.get(0);
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL, requestedUrlCookie.getName());

        // step 2: send credentials to the callback url (callback from the identity provider)
        request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{requestedUrlCookie});
        request.setRequestURL(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Clients.DEFAULT_CLIENT_NAME_PARAMETER + "=" + Clients.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS);
        request.addParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER, "true");
        request.addParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER, CLIENT_CLASS);
        request.addHeader("Authorization", "Basic amxlbGV1OmpsZWxldQ==");
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the original url
        assertEquals(302, response.getStatus());
        assertEquals(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL, response.getHeaders().get("Location"));
        // we should have 3 cookies among with the user profile
        cookies = response.getCookies();
        Map<String, String> mapCookies = new HashMap<>();
        assertEquals(3, cookies.size());
        for (final Cookie cookie : cookies) {
            mapCookies.put(cookie.getName(), cookie.getValue());
        }
        assertNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + CLIENT_CLASS + "$attemptedAuthentication"));
        assertNotNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILE));
        assertNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL));

        // step 3: turn pac4j identity into KnoxSSO identity
        request = new MockHttpServletRequest();
        request.setCookies(cookies.toArray(new Cookie[cookies.size()]));
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        assertEquals(0, response.getStatus());
        adapter.doFilter(request, response, filterChain);
        cookies = response.getCookies();
        assertEquals(1, cookies.size());
        final Cookie userProfileCookie = cookies.get(0);
        // the user profile has been cleaned
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILE, userProfileCookie.getName());
        assertNull(userProfileCookie.getValue());
        assertEquals(USERNAME, adapter.getTestIdentifier());
    }
}
