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

import org.apache.knox.gateway.preauth.filter.PreAuthValidationException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServiceAccountValidatorTest {
    private static final String SPIFFE = "spiffe://cluster.local/ns/demo/sa/demo-app";
    private static final String NS = "demo";
    private static final String SA = "demo-app";
    private static final String ANNOTATION = "knox.apache.org/owner-username";

    private K8sServiceAccountResolver resolver;
    private ServiceAccountValidator validator;

    @Before
    public void setUp() throws Exception {
        resolver = EasyMock.createMock(K8sServiceAccountResolver.class);
        validator = new ServiceAccountValidator() {
            @Override
            protected K8sServiceAccountResolver createResolver(Duration duration, long maxSize) {
                return resolver;
            }
        };
        final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
        EasyMock.expect(filterConfig.getInitParameter(ServiceAccountValidator.CACHE_TTL_SECONDS_PARAM)).andReturn("120").anyTimes();
        EasyMock.expect(filterConfig.getInitParameter(ServiceAccountValidator.CACHE_MAX_SIZE_PARAM)).andReturn("100").anyTimes();
        EasyMock.replay(filterConfig);
        validator.init(filterConfig);
    }

    @Test
    public void testName() {
        assertEquals(ServiceAccountValidator.VALIDATION_METHOD_VALUE, validator.getName());
    }

    @Test
    public void testAcceptWhenAnnotationMatchesUserHeader() throws PreAuthValidationException {
        EasyMock.expect(resolver.getAnnotation(NS, SA, ANNOTATION)).andReturn(Optional.of("bob"));
        EasyMock.replay(resolver);

        assertTrue(validator.validate(request(SPIFFE, "bob"), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testRejectWhenAnnotationDiffersFromUserHeader() throws PreAuthValidationException {
        EasyMock.expect(resolver.getAnnotation(NS, SA, ANNOTATION)).andReturn(Optional.of("alice"));
        EasyMock.replay(resolver);

        assertFalse(validator.validate(request(SPIFFE, "bob"), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testRejectWhenSpiffeHeaderMissing() throws PreAuthValidationException {
        EasyMock.replay(resolver);
        assertFalse(validator.validate(request(null, "bob"), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testRejectWhenUserHeaderMissing() throws PreAuthValidationException {
        EasyMock.replay(resolver);
        assertFalse(validator.validate(request(SPIFFE, null), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testRejectWhenSpiffeUnparseable() throws PreAuthValidationException {
        EasyMock.replay(resolver);
        assertFalse(validator.validate(request("not-a-spiffe-id", "bob"), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testRejectWhenServiceAccountAnnotationMissing() throws PreAuthValidationException {
        EasyMock.expect(resolver.getAnnotation(NS, SA, ANNOTATION)).andReturn(Optional.empty());
        EasyMock.replay(resolver);

        assertFalse(validator.validate(request(SPIFFE, "bob"), defaultConfig()));
        EasyMock.verify(resolver);
    }

    @Test
    public void testHonorCustomHeaderAndAnnotationConfig() throws PreAuthValidationException {
        final String customAnnotation = "example.com/owner-username";
        EasyMock.expect(resolver.getAnnotation(NS, SA, customAnnotation)).andReturn(Optional.of("bob"));
        EasyMock.replay(resolver);

        final FilterConfig cfg = EasyMock.createMock(FilterConfig.class);
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.SPIFFE_HEADER_PARAM))
                .andReturn("X-Custom-Spiffe").anyTimes();
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.USER_HEADER_PARAM))
                .andReturn("x-custom-user").anyTimes();
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.USER_ANNOTATION_PARAM))
                .andReturn(customAnnotation).anyTimes();
        EasyMock.replay(cfg);

        final HttpServletRequest req = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader("X-Custom-Spiffe")).andReturn(SPIFFE).anyTimes();
        EasyMock.expect(req.getHeader("x-custom-user")).andReturn("bob").anyTimes();
        EasyMock.replay(req);

        assertTrue(validator.validate(req, cfg));
        EasyMock.verify(resolver, cfg, req);
    }

    private static FilterConfig defaultConfig() {
        final FilterConfig cfg = EasyMock.createMock(FilterConfig.class);
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.SPIFFE_HEADER_PARAM))
                .andReturn(null).anyTimes();
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.USER_HEADER_PARAM))
                .andReturn(null).anyTimes();
        EasyMock.expect(cfg.getInitParameter(ServiceAccountValidator.USER_ANNOTATION_PARAM))
                .andReturn(null).anyTimes();
        EasyMock.replay(cfg);
        return cfg;
    }

    private static HttpServletRequest request(String spiffe, String user) {
        final HttpServletRequest req = EasyMock.createMock(HttpServletRequest.class);
        EasyMock.expect(req.getHeader(ServiceAccountValidator.SPIFFE_HEADER_DEFAULT))
                .andReturn(spiffe).anyTimes();
        EasyMock.expect(req.getHeader(ServiceAccountValidator.USER_HEADER_DEFAULT))
                .andReturn(user).anyTimes();
        EasyMock.replay(req);
        return req;
    }
}
