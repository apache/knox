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

import com.github.benmanes.caffeine.cache.Ticker;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccountList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.ServiceAccountResource;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class K8sServiceAccountResolverTest {

    private static final String NS = "demo";
    private static final String SA = "demo-app";
    private static final String ANNOTATION = "knox.apache.org/owner-username";

    private KubernetesClient client;
    private final MixedOperation<ServiceAccount, ServiceAccountList, ServiceAccountResource> mixed =
            EasyMock.createMock(MixedOperation.class);
    private final NonNamespaceOperation<ServiceAccount, ServiceAccountList, ServiceAccountResource> namespaced =
            EasyMock.createMock(NonNamespaceOperation.class);
    private ServiceAccountResource resource;

    @Before
    public void setUp() {
        client = EasyMock.createMock(KubernetesClient.class);
        resource = EasyMock.createMock(ServiceAccountResource.class);
    }

    @Test
    public void testReturnAnnotationWhenPresent() {
        expectFetchReturning(saWithAnnotations(Map.of(ANNOTATION, "bob")));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        verifyAll();
    }

    @Test
    public void testReturnEmptyWhenAnnotationMissingFromExistingSA() {
        expectFetchReturning(saWithAnnotations(Map.of("other-key", "x")));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        verifyAll();
    }

    @Test
    public void testCacheSuccessfulLookups() {
        expectFetchReturning(saWithAnnotations(Map.of(ANNOTATION, "bob")));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        verifyAll();
    }

    @Test
    public void testCacheSAAbsenceAsEmpty() {
        expectFetchReturning(null);
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        verifyAll();
    }

    @Test
    public void testCacheSAWithoutAnnotationsAsEmpty() {
        expectFetchReturning(saWithAnnotations(null));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        verifyAll();
    }

    @Test
    public void testCacheSAWithoutMetadataAsEmpty() {
        expectFetchReturning(new ServiceAccountBuilder().build()); // no metadata
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        verifyAll();
    }

    @Test
    public void testApiFailureIsNotCached() {
        EasyMock.expect(client.serviceAccounts()).andReturn(mixed).times(2);
        EasyMock.expect(mixed.inNamespace(NS)).andReturn(namespaced).times(2);
        EasyMock.expect(namespaced.withName(SA)).andReturn(resource).times(2);
        EasyMock.expect(resource.get())
                .andThrow(new KubernetesClientException("503 boom"))
                .andReturn(saWithAnnotations(Map.of(ANNOTATION, "bob")));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertFalse(r.getAnnotation(NS, SA, ANNOTATION).isPresent());
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        verifyAll();
    }

    @Test
    public void testDifferentAnnotationKeysOnSameSAShareSingleFetch() {
        Map<String, String> annotations = new HashMap<>();
        annotations.put(ANNOTATION, "bob");
        annotations.put("other-key", "value-2");
        expectFetchReturning(saWithAnnotations(annotations));
        replayAll();

        K8sServiceAccountResolver r = new K8sServiceAccountResolver(client, Duration.ofSeconds(60), 100);
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        assertEquals(Optional.of("value-2"), r.getAnnotation(NS, SA, "other-key"));
        verifyAll();
    }

    @Test
    public void testCacheEntryExpiresAfterTtl() {
        EasyMock.expect(client.serviceAccounts()).andReturn(mixed).times(2);
        EasyMock.expect(mixed.inNamespace(NS)).andReturn(namespaced).times(2);
        EasyMock.expect(namespaced.withName(SA)).andReturn(resource).times(2);
        EasyMock.expect(resource.get())
                .andReturn(saWithAnnotations(Map.of(ANNOTATION, "bob")))
                .andReturn(saWithAnnotations(Map.of(ANNOTATION, "alice")));
        replayAll();

        FakeTicker ticker = new FakeTicker();
        K8sServiceAccountResolver r =
                new K8sServiceAccountResolver(client, Duration.ofSeconds(10), 100, ticker);

        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        assertEquals(Optional.of("bob"), r.getAnnotation(NS, SA, ANNOTATION));
        ticker.advance(Duration.ofSeconds(11));
        assertEquals(Optional.of("alice"), r.getAnnotation(NS, SA, ANNOTATION));
        verifyAll();
    }

    private void expectFetchReturning(ServiceAccount sa) {
        EasyMock.expect(client.serviceAccounts()).andReturn(mixed);
        EasyMock.expect(mixed.inNamespace(NS)).andReturn(namespaced);
        EasyMock.expect(namespaced.withName(SA)).andReturn(resource);
        EasyMock.expect(resource.get()).andReturn(sa);
    }

    private static ServiceAccount saWithAnnotations(Map<String, String> annotations) {
        ObjectMeta meta = new ObjectMetaBuilder()
                .withNamespace(NS)
                .withName(SA)
                .withAnnotations(annotations)
                .build();
        return new ServiceAccountBuilder().withMetadata(meta).build();
    }

    private void replayAll() {
        EasyMock.replay(client, mixed, namespaced, resource);
    }

    private void verifyAll() {
        EasyMock.verify(client, mixed, namespaced, resource);
    }

    private static final class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong(0);

        void advance(Duration d) {
            nanos.addAndGet(d.toNanos());
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
