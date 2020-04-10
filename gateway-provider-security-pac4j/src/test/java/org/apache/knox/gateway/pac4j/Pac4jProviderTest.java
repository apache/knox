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

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter;
import org.apache.knox.gateway.pac4j.filter.Pac4jIdentityAdapter;
import org.apache.knox.gateway.pac4j.session.KnoxSessionStore;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.easymock.EasyMock;
import org.junit.Test;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * This class simulates a full authentication process using pac4j.
 */
public class Pac4jProviderTest {

    private static final String LOCALHOST = "127.0.0.1";
    private static final String HADOOP_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/sandox/webhdfs/v1/tmp?op=LISTSTATUS";
    private static final String KNOXSSO_SERVICE_URL = "https://" + LOCALHOST + ":8443/gateway/idp/api/v1/websso";
    private static final String PAC4J_CALLBACK_URL = KNOXSSO_SERVICE_URL;
    private static final String ORIGINAL_URL = "originalUrl";
    private static final String CLUSTER_NAME = "knox";
    private static final String PAC4J_PASSWORD = "pwdfortest";
    private static final String CLIENT_CLASS = IndirectBasicAuthClient.class.getSimpleName();
    private static final String USERNAME = "jleleu";

    @Test
    public void test() throws Exception {
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
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(CLUSTER_NAME);
        EasyMock.replay(context);

        final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(config.getServletContext()).andReturn(context);
        EasyMock.expect(config.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(PAC4J_CALLBACK_URL);
        EasyMock.expect(config.getInitParameter("clientName")).andReturn(Pac4jDispatcherFilter.TEST_BASIC_AUTH);
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

        // step 1: call the KnoxSSO service with an original url pointing to an Hadoop service (redirected by the SSOCookieProvider)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setCookies(new Cookie[0]);
        request.setServerName(LOCALHOST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the idp topology
        assertEquals(302, response.getStatus());
        assertEquals(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS, response.getHeaders().get("Location"));
        // we should have one cookie for the saved requested url
        List<Cookie> cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie requestedUrlCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL, requestedUrlCookie.getName());

        // step 2: send credentials to the callback url (callback from the identity provider)
        request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{requestedUrlCookie});
        request.setRequestURL(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS);
        request.addParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER, "true");
        request.addParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, CLIENT_CLASS);
        request.addHeader("Authorization", "Basic amxlbGV1OmpsZWxldQ==");
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the original url
        assertEquals(302, response.getStatus());
        assertEquals(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL, response.getHeaders().get("Location"));
        // we should have 2 cookies among with the user profile
        cookies = response.getCookies();
        Map<String, String> mapCookies = new HashMap<>();
        assertEquals(2, cookies.size());
        for (final Cookie cookie : cookies) {
            mapCookies.put(cookie.getName(), cookie.getValue());
        }
        assertNotNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES));
        assertNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL));

        // step 3: turn pac4j identity into KnoxSSO identity
        request = new MockHttpServletRequest();
        request.setCookies(cookies.toArray(new Cookie[0]));
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        assertEquals(0, response.getStatus());
        adapter.doFilter(request, response, filterChain);
        cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie userProfileCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        // the user profile has been cleaned
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES, userProfileCookie.getName());
        assertNull(userProfileCookie.getValue());
        assertEquals(USERNAME, adapter.getTestIdentifier());
    }

    @Test
    public void testValidIdAttribute() throws Exception {
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
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(CLUSTER_NAME);
        EasyMock.replay(context);

        final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(config.getServletContext()).andReturn(context);
        EasyMock.expect(config.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(PAC4J_CALLBACK_URL);
        EasyMock.expect(config.getInitParameter("clientName")).andReturn(Pac4jDispatcherFilter.TEST_BASIC_AUTH);
        EasyMock.expect(config.getInitParameter(Pac4jIdentityAdapter.PAC4J_ID_ATTRIBUTE)).andReturn("username");
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

        // step 1: call the KnoxSSO service with an original url pointing to an Hadoop service (redirected by the SSOCookieProvider)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setCookies(new Cookie[0]);
        request.setServerName(LOCALHOST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the idp topology
        assertEquals(302, response.getStatus());
        assertEquals(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS, response.getHeaders().get("Location"));
        // we should have one cookie for the saved requested url
        List<Cookie> cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie requestedUrlCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL, requestedUrlCookie.getName());

        // step 2: send credentials to the callback url (callback from the identity provider)
        request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{requestedUrlCookie});
        request.setRequestURL(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS);
        request.addParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER, "true");
        request.addParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, CLIENT_CLASS);
        request.addHeader("Authorization", "Basic amxlbGV1OmpsZWxldQ==");
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the original url
        assertEquals(302, response.getStatus());
        assertEquals(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL, response.getHeaders().get("Location"));
        // we should have 2 cookies among with the user profile
        cookies = response.getCookies();
        Map<String, String> mapCookies = new HashMap<>();
        assertEquals(2, cookies.size());
        for (final Cookie cookie : cookies) {
            mapCookies.put(cookie.getName(), cookie.getValue());
        }
        assertNotNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES));
        assertNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL));

        // step 3: turn pac4j identity into KnoxSSO identity
        request = new MockHttpServletRequest();
        request.setCookies(cookies.toArray(new Cookie[0]));
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        assertEquals(0, response.getStatus());
        adapter.doFilter(request, response, filterChain);
        cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie userProfileCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        // the user profile has been cleaned
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES, userProfileCookie.getName());
        assertNull(userProfileCookie.getValue());
        assertEquals(USERNAME, adapter.getTestIdentifier());
    }
    @Test
    public void testInvalidIdAttribute() throws Exception {
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
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(CLUSTER_NAME);
        EasyMock.replay(context);

        final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(config.getServletContext()).andReturn(context);
        EasyMock.expect(config.getInitParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_URL)).andReturn(PAC4J_CALLBACK_URL);
        EasyMock.expect(config.getInitParameter("clientName")).andReturn(Pac4jDispatcherFilter.TEST_BASIC_AUTH);
        EasyMock.expect(config.getInitParameter(Pac4jIdentityAdapter.PAC4J_ID_ATTRIBUTE)).andReturn("larry");
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

        // step 1: call the KnoxSSO service with an original url pointing to an Hadoop service (redirected by the SSOCookieProvider)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setCookies(new Cookie[0]);
        request.setServerName(LOCALHOST);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the idp topology
        assertEquals(302, response.getStatus());
        assertEquals(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS, response.getHeaders().get("Location"));
        // we should have one cookie for the saved requested url
        List<Cookie> cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie requestedUrlCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL, requestedUrlCookie.getName());

        // step 2: send credentials to the callback url (callback from the identity provider)
        request = new MockHttpServletRequest();
        request.setCookies(new Cookie[]{requestedUrlCookie});
        request.setRequestURL(PAC4J_CALLBACK_URL + "?" + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER + "=true&" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER + "=" + CLIENT_CLASS);
        request.addParameter(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER, "true");
        request.addParameter(Pac4jConstants.DEFAULT_CLIENT_NAME_PARAMETER, CLIENT_CLASS);
        request.addHeader("Authorization", "Basic amxlbGV1OmpsZWxldQ==");
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        // it should be a redirection to the original url
        assertEquals(302, response.getStatus());
        assertEquals(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL, response.getHeaders().get("Location"));
        // we should have 2 cookies among with the user profile
        cookies = response.getCookies();
        Map<String, String> mapCookies = new HashMap<>();
        assertEquals(2, cookies.size());
        for (final Cookie cookie : cookies) {
            mapCookies.put(cookie.getName(), cookie.getValue());
        }
        assertNotNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES));
        assertNull(mapCookies.get(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.REQUESTED_URL));

        // step 3: turn pac4j identity into KnoxSSO identity
        request = new MockHttpServletRequest();
        request.setCookies(cookies.toArray(new Cookie[0]));
        request.setRequestURL(KNOXSSO_SERVICE_URL + "?" + ORIGINAL_URL + "=" + HADOOP_SERVICE_URL);
        request.setServerName(LOCALHOST);
        response = new MockHttpServletResponse();
        filterChain = EasyMock.createNiceMock(FilterChain.class);
        dispatcher.doFilter(request, response, filterChain);
        assertEquals(0, response.getStatus());
        adapter.doFilter(request, response, filterChain);
        cookies = response.getCookies();
        assertEquals(3, cookies.size());
        final Cookie userProfileCookie = cookies.stream()
            .filter(c -> (KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES).equals(c.getName()))
            .collect(Collectors.toList()).get(0);
        // the user profile has been cleaned
        assertEquals(KnoxSessionStore.PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES, userProfileCookie.getName());
        assertNull(userProfileCookie.getValue());
        assertEquals(USERNAME, adapter.getTestIdentifier());
    }

}
