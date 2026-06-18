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
package org.apache.knox.gateway.preauth.k8s;

import org.apache.knox.gateway.preauth.filter.PreAuthService;
import org.apache.knox.gateway.preauth.filter.PreAuthValidator;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class K8sPreAuthFederationFilterTest {

    @Test
    public void testInitResolvesK8sValidatorFromContributorInjectedParam() throws ServletException {
        TestableFilter filter = new TestableFilter();
        FilterConfig cfg = niceCfg();
        filter.init(cfg);

        List<PreAuthValidator> validators = filter.getValidators();
        assertEquals(1, validators.size());
        assertEquals(ServiceAccountValidator.VALIDATION_METHOD_VALUE, validators.get(0).getName());
        assertNotNull("init must construct a resolver", filter.lastCreatedResolver);
    }

    @Test
    public void testInitRejectsNonPositiveTtl() {
        TestableFilter filter = new TestableFilter();
        FilterConfig cfg = niceCfg(ServiceAccountValidator.CACHE_TTL_SECONDS_PARAM, "0");
        try {
            filter.init(cfg);
            fail("expected ServletException for ttl=0");
        } catch (ServletException expected) {
            assertTrue(expected.getMessage().contains(
                    ServiceAccountValidator.CACHE_TTL_SECONDS_PARAM));
        }
        assertNull("resolver must not be created on bad config", filter.lastCreatedResolver);
    }

    @Test
    public void testInitRejectsNegativeMaxSize() {
        TestableFilter filter = new TestableFilter();
        FilterConfig cfg = niceCfg(ServiceAccountValidator.CACHE_MAX_SIZE_PARAM, "-1");
        try {
            filter.init(cfg);
            fail("expected ServletException for maxSize=-1");
        } catch (ServletException expected) {
            assertTrue(expected.getMessage().contains(
                    ServiceAccountValidator.CACHE_MAX_SIZE_PARAM));
        }
        assertNull("resolver must not be created on bad config", filter.lastCreatedResolver);
    }

    @Test
    public void testGetPrimaryPrincipalUsesDefaultUserHeaderWhenUnset() throws ServletException {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg());

        HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andReturn("alice").anyTimes();
        EasyMock.replay(req);

        assertEquals("alice", filter.getPrimaryPrincipal(req));
    }

    @Test
    public void testGetPrimaryPrincipalHonorsCustomUserHeader() throws ServletException {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg(ServiceAccountValidator.USER_HEADER_PARAM, "X-My-User"));

        HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader("X-My-User")).andReturn("bob").anyTimes();
        EasyMock.replay(req);

        assertEquals("bob", filter.getPrimaryPrincipal(req));
    }

    @Test
    public void testEmptyCustomUserHeaderFallsBackToDefault() throws ServletException {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg(ServiceAccountValidator.USER_HEADER_PARAM, ""));

        HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andReturn("carol").anyTimes();
        EasyMock.replay(req);

        assertEquals("carol", filter.getPrimaryPrincipal(req));
    }

    @Test
    public void testGetPrimaryPrincipalReturnsNullWhenHeaderAbsent() throws ServletException {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg());

        HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andReturn(null).anyTimes();
        EasyMock.replay(req);

        assertNull(filter.getPrimaryPrincipal(req));
    }

    @Test
    public void testDoFilterBindsResolverAttributeAndRemovesItAfterChain() throws Exception {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg());

        HttpServletRequest req = EasyMock.createMock(HttpServletRequest.class);
        req.setAttribute(EasyMock.eq(ServiceAccountValidator.RESOLVER_REQUEST_ATTR),
                EasyMock.same(filter.lastCreatedResolver));
        EasyMock.expectLastCall();
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andReturn(null);
        req.removeAttribute(ServiceAccountValidator.RESOLVER_REQUEST_ATTR);
        EasyMock.expectLastCall();
        EasyMock.replay(req);

        HttpServletResponse resp = EasyMock.createMock(HttpServletResponse.class);
        resp.sendError(EasyMock.eq(HttpServletResponse.SC_FORBIDDEN), EasyMock.anyString());
        EasyMock.expectLastCall();
        EasyMock.replay(resp);

        FilterChain chain = EasyMock.createMock(FilterChain.class);
        EasyMock.replay(chain);

        filter.doFilter(req, resp, chain);

        EasyMock.verify(req, resp, chain);
    }

    @Test
    public void testDoFilterRemovesResolverAttributeEvenIfChainThrows() throws Exception {
        TestableFilter filter = new TestableFilter();
        filter.init(niceCfg());

        HttpServletRequest req = EasyMock.createMock(HttpServletRequest.class);
        req.setAttribute(EasyMock.eq(ServiceAccountValidator.RESOLVER_REQUEST_ATTR),
                EasyMock.same(filter.lastCreatedResolver));
        EasyMock.expectLastCall();
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andThrow(new RuntimeException("boom"));
        req.removeAttribute(ServiceAccountValidator.RESOLVER_REQUEST_ATTR);
        EasyMock.expectLastCall();
        EasyMock.replay(req);

        HttpServletResponse resp = EasyMock.createMock(HttpServletResponse.class);
        EasyMock.replay(resp);
        FilterChain chain = EasyMock.createMock(FilterChain.class);
        EasyMock.replay(chain);

        try {
            filter.doFilter(req, resp, chain);
            fail("expected RuntimeException to propagate");
        } catch (RuntimeException expected) {
            assertEquals("boom", expected.getMessage());
        }
        EasyMock.verify(req, resp, chain);
    }

    @Test
    public void testDestroyClosesResolverOnce() throws ServletException {
        K8sServiceAccountResolver resolver = EasyMock.createMock(K8sServiceAccountResolver.class);
        resolver.close();
        EasyMock.expectLastCall().once();
        EasyMock.replay(resolver);

        TestableFilter filter = new TestableFilter(resolver);
        filter.init(niceCfg());
        assertSame(resolver, filter.lastCreatedResolver);

        filter.destroy();
        // Second destroy must not double-close.
        filter.destroy();

        EasyMock.verify(resolver);
    }

    @Test
    public void testDestroyWithoutInitDoesNotThrow() {
        K8sPreAuthFederationFilter filter = new K8sPreAuthFederationFilter();
        filter.destroy();
    }

    private static FilterConfig niceCfg() {
        FilterConfig cfg = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(cfg.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM))
                .andReturn(ServiceAccountValidator.VALIDATION_METHOD_VALUE).anyTimes();
        EasyMock.replay(cfg);
        return cfg;
    }

    private static FilterConfig niceCfg(String paramName, String paramValue) {
        FilterConfig cfg = EasyMock.createNiceMock(FilterConfig.class);
        EasyMock.expect(cfg.getInitParameter(PreAuthService.VALIDATION_METHOD_PARAM))
                .andReturn(ServiceAccountValidator.VALIDATION_METHOD_VALUE).anyTimes();
        EasyMock.expect(cfg.getInitParameter(paramName)).andReturn(paramValue).anyTimes();
        EasyMock.replay(cfg);
        return cfg;
    }

    private static final class TestableFilter extends K8sPreAuthFederationFilter {
        private final K8sServiceAccountResolver fixed;
        K8sServiceAccountResolver lastCreatedResolver;

        TestableFilter() {
            this(null);
        }

        TestableFilter(K8sServiceAccountResolver fixed) {
            this.fixed = fixed;
        }

        @Override
        protected K8sServiceAccountResolver createResolver(Duration ttl, long maxSize) {
            K8sServiceAccountResolver r = fixed != null
                    ? fixed
                    : EasyMock.createNiceMock(K8sServiceAccountResolver.class);
            lastCreatedResolver = r;
            return r;
        }
    }
}
