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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class K8sServiceAccountResolver implements Closeable {
  private static final K8sPreAuthMessages LOG = MessagesFactory.get(K8sPreAuthMessages.class);

  private final KubernetesClient client;
  private final Cache<Key, Optional<Map<String, String>>> annotationsCache;

  public K8sServiceAccountResolver(Duration ttl, long maxSize) {
    this(new KubernetesClientBuilder().build(), ttl, maxSize, Ticker.systemTicker());
  }

  K8sServiceAccountResolver(KubernetesClient client, Duration ttl, long maxSize) {
    this(client, ttl, maxSize, Ticker.systemTicker());
  }

  K8sServiceAccountResolver(KubernetesClient client, Duration ttl, long maxSize, Ticker ticker) {
    this.client = Objects.requireNonNull(client);
    this.annotationsCache = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(maxSize)
        .ticker(ticker)
        .build();
  }

  public Optional<String> getAnnotation(String namespace, String serviceAccount, String annotationKey) {
    final Key key = new Key(namespace, serviceAccount);
    final Optional<Map<String, String>> annotations;
    try {
      annotations = annotationsCache.get(key, this::fetchAnnotations);
    } catch (K8sLookupException e) {
      LOG.failedToLoadServiceAccount(namespace, serviceAccount, e.getCause());
      return Optional.empty();
    }
    if (annotations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(annotations.get().get(annotationKey));
  }

  private Optional<Map<String, String>> fetchAnnotations(Key key) {
    final ServiceAccount sa;
    try {
      sa = client.serviceAccounts()
          .inNamespace(key.namespace)
          .withName(key.serviceAccount)
          .get();
    } catch (Exception e) {
      // Propagate so Caffeine does NOT cache the failure: a transient API error
      // must not poison the cache for the success TTL.
      throw new K8sLookupException(e);
    }
    if (sa == null || sa.getMetadata() == null) {
      return Optional.empty();
    }
    final Map<String, String> annotations = sa.getMetadata().getAnnotations();
    return annotations == null ? Optional.empty() : Optional.of(annotations);
  }

  @Override
  public void close() {
      client.close();
  }

  private static final class Key {
    final String namespace;
    final String serviceAccount;

    Key(String namespace, String serviceAccount) {
      this.namespace = namespace;
      this.serviceAccount = serviceAccount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Key)) {
        return false;
      }
      Key k = (Key) o;
      return namespace.equals(k.namespace) && serviceAccount.equals(k.serviceAccount);
    }

    @Override
    public int hashCode() {
      return Objects.hash(namespace, serviceAccount);
    }
  }
}
