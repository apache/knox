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
package org.apache.knox.gateway.audit.log4j.appender;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

@Plugin(
    name = "AuditGateway",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE)
public class AuditGatewayAppender extends AbstractAppender {
  private static final Logger LOG = LogManager.getLogger(AuditGatewayAppender.class);
  private static final long DROP_WARN_INTERVAL_MS = 60_000L;
  private static final long POLL_MS = 250L;
  private static final ContentType JSON = ContentType.create("application/json");
  static final int MAX_WORKERS = 16;

  private final URI uri;
  private final String sourceSystem;
  private final String podName;
  private final BlockingQueue<String> queue;
  private final int requestTimeoutMs;
  private final long drainTimeoutMs;
  private final int workerCount;
  private final List<Thread> workers = new ArrayList<>();
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong lastDropWarn = new AtomicLong();
  private CloseableHttpClient client;
  private Thread shutdownHook;

  AuditGatewayAppender(String name, Filter filter, URI uri, String sourceSystem, String podName,
                       int queueSize, long requestTimeoutMs, long drainTimeoutMs, int workers) {
    super(name, filter, null, true, Property.EMPTY_ARRAY);
    this.uri = uri;
    this.sourceSystem = sourceSystem;
    this.podName = podName;
    this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    this.requestTimeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, requestTimeoutMs));
    this.drainTimeoutMs = Math.max(0L, drainTimeoutMs);
    this.workerCount = Math.min(MAX_WORKERS, Math.max(1, workers));
  }

  @PluginFactory
  public static AuditGatewayAppender createAppender(
      @PluginAttribute("name") String name,
      @PluginElement("Filter") Filter filter,
      @PluginAttribute("endpoint") String endpoint,
      @PluginAttribute("path") String path,
      @PluginAttribute("sourceSystem") String sourceSystem,
      @PluginAttribute("podName") String podName,
      @PluginAttribute(value = "queueSize", defaultInt = 2000) int queueSize,
      @PluginAttribute(value = "requestTimeoutMs", defaultLong = 2500L) long requestTimeoutMs,
      @PluginAttribute(value = "drainTimeoutMs", defaultLong = 5000L) long drainTimeoutMs,
      @PluginAttribute(value = "workers", defaultInt = 1) int workers) {
    return new AuditGatewayAppender(name, filter, resolveUri(endpoint, path), sourceSystem, podName,
        queueSize, requestTimeoutMs, drainTimeoutMs, workers);
  }

  static URI resolveUri(String endpoint, String path) {
    if (StringUtils.isBlank(endpoint)) {
      return null;
    }
    String value = endpoint.trim();
    if (!value.contains("://")) {
      value = "http://" + value;
    }
    URI parsed = URI.create(value);
    if (StringUtils.isNotBlank(path)) {
      String p = path.trim();
      parsed = parsed.resolve(p.startsWith("/") ? p : "/" + p);
    }
    return parsed;
  }

  boolean isEnabled() {
    return uri != null;
  }

  long getDropped() {
    return dropped.get();
  }

  int getWorkerCount() {
    return workerCount;
  }

  @Override
  public void start() {
    if (isEnabled()) {
      RequestConfig requestConfig = RequestConfig.custom()
          .setConnectTimeout(requestTimeoutMs)
          .setConnectionRequestTimeout(requestTimeoutMs)
          .setSocketTimeout(requestTimeoutMs)
          .build();
      client = HttpClients.custom()
          .setDefaultRequestConfig(requestConfig)
          .disableAutomaticRetries()
          .disableCookieManagement()
          .disableRedirectHandling()
          .setMaxConnPerRoute(workerCount)
          .setMaxConnTotal(workerCount)
          .build();
      running.set(true);
      for (int i = 0; i < workerCount; i++) {
        Thread worker = new Thread(this::run, "audit-gateway-" + getName() + "-" + i);
        worker.setDaemon(true);
        worker.start();
        workers.add(worker);
      }
      shutdownHook = new Thread(() -> stop(drainTimeoutMs, TimeUnit.MILLISECONDS),
          "audit-gateway-drain-" + getName());
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    super.start();
  }

  @Override
  public void append(LogEvent event) {
    if (!isEnabled() || !running.get()) {
      return;
    }
    String body;
    try {
      body = AuditEventMapper.toJson(event, sourceSystem, podName);
    } catch (RuntimeException e) {
      LOG.error("Audit gateway appender {} could not map an audit event", getName(), e);
      return;
    }
    if (!queue.offer(body)) {
      long count = dropped.incrementAndGet();
      long now = System.currentTimeMillis();
      long last = lastDropWarn.get();
      if (now - last >= DROP_WARN_INTERVAL_MS && lastDropWarn.compareAndSet(last, now)) {
        LOG.warn("Audit gateway appender {} queue is full; {} events dropped so far", getName(), count);
      }
    }
  }

  @Override
  public synchronized boolean stop(long timeout, TimeUnit timeUnit) {
    if (isStopped()) {
      return true;
    }
    setStopping();
    if (shutdownHook != null && Thread.currentThread() != shutdownHook) {
      try {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
      } catch (IllegalStateException e) {
        LOG.debug("Audit gateway appender {} is stopping during JVM shutdown", getName());
      }
    }
    boolean stopped = super.stop(timeout, timeUnit, false);
    running.set(false);
    if (!workers.isEmpty()) {
      long deadline = System.currentTimeMillis() + Math.max(1L, timeout > 0 ? timeUnit.toMillis(timeout) : drainTimeoutMs);
      try {
        for (Thread worker : workers) {
          long remaining = deadline - System.currentTimeMillis();
          if (remaining > 0) {
            worker.join(remaining);
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      int undelivered = queue.size();
      queue.clear();
      if (undelivered > 0) {
        LOG.warn("Audit gateway appender {} stopped with {} undelivered events", getName(), undelivered);
      }
    }
    if (client != null) {
      try {
        client.close();
      } catch (IOException e) {
        LOG.debug("Audit gateway appender {} could not close its HTTP client", getName(), e);
      }
    }
    setStopped();
    return stopped;
  }

  private void run() {
    while (running.get() || !queue.isEmpty()) {
      String body;
      try {
        body = queue.poll(POLL_MS, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      if (body != null) {
        send(body);
      }
    }
  }

  private void send(String body) {
    HttpPost post = new HttpPost(uri);
    post.setEntity(new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8), JSON));
    try (CloseableHttpResponse response = client.execute(post)) {
      int status = response.getStatusLine().getStatusCode();
      String responseBody = response.getEntity() == null
          ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      if (status < 200 || status >= 300) {
        LOG.error("Audit gateway appender {} could not deliver an event, HTTP {}: {}", getName(), status, responseBody);
      }
    } catch (IOException | RuntimeException e) {
      LOG.error("Audit gateway appender {} could not deliver an event: {}", getName(), e.toString());
    }
  }
}
