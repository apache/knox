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

import static org.apache.knox.gateway.audit.log4j.appender.AuditEvents.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AuditGatewayAppenderTest {
  private static final String EVENTS_PATH = "/api/v1/audit/events";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ServerSocket server;
  private Thread acceptor;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final BlockingQueue<Integer> statuses = new LinkedBlockingQueue<>();
  private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();
  private final AtomicInteger arrived = new AtomicInteger();
  private AuditGatewayAppender appender;

  private static final class Received {
    final String method;
    final String path;
    final String contentType;
    final String body;

    Received(String method, String path, String contentType, String body) {
      this.method = method;
      this.path = path;
      this.contentType = contentType;
      this.body = body;
    }
  }

  @Before
  public void setUp() throws IOException {
    server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    acceptor = new Thread(this::accept, "audit-gateway-stub");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  @After
  public void tearDown() throws Exception {
    if (appender != null) {
      appender.stop(1, TimeUnit.SECONDS);
    }
    CountDownLatch g = gate.get();
    if (g != null) {
      g.countDown();
    }
    server.close();
    acceptor.join(2000L);
  }

  private void accept() {
    while (!server.isClosed()) {
      try {
        Socket socket = server.accept();
        Thread handler = new Thread(() -> handle(socket), "audit-gateway-stub-conn");
        handler.setDaemon(true);
        handler.start();
      } catch (IOException e) {
        return;
      }
    }
  }

  private void handle(Socket socket) {
    try (Socket s = socket;
         InputStream in = new BufferedInputStream(s.getInputStream());
         OutputStream out = s.getOutputStream()) {
      String requestLine = readLine(in);
      String contentType = null;
      int length = 0;
      for (String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
        int colon = line.indexOf(':');
        String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        String value = line.substring(colon + 1).trim();
        if ("content-type".equals(name)) {
          contentType = value;
        } else if ("content-length".equals(name)) {
          length = Integer.parseInt(value);
        }
      }
      String body = new String(in.readNBytes(length), StandardCharsets.UTF_8);
      arrived.incrementAndGet();
      CountDownLatch g = gate.get();
      if (g != null) {
        g.await(20, TimeUnit.SECONDS);
      }
      String[] parts = requestLine.split(" ");
      received.add(new Received(parts[0], parts[1], contentType, body));
      Integer status = statuses.poll();
      byte[] response = "{\"detail\":\"bad\"}".getBytes(StandardCharsets.UTF_8);
      String head = "HTTP/1.1 " + (status == null ? 200 : status) + " X\r\n"
          + "Content-Type: application/json\r\n"
          + "Content-Length: " + response.length + "\r\n"
          + "Connection: close\r\n\r\n";
      out.write(head.getBytes(StandardCharsets.US_ASCII));
      out.write(response);
      out.flush();
    } catch (IOException e) {
      return;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String readLine(InputStream in) throws IOException {
    ByteArrayOutputStream line = new ByteArrayOutputStream();
    for (int c = in.read(); c != -1 && c != '\n'; c = in.read()) {
      if (c != '\r') {
        line.write(c);
      }
    }
    return line.toString(StandardCharsets.US_ASCII);
  }

  private String endpoint() {
    return "127.0.0.1:" + server.getLocalPort();
  }

  private AuditGatewayAppender start(String endpoint, int queueSize) {
    return start(endpoint, queueSize, 2000L);
  }

  private AuditGatewayAppender start(String endpoint, int queueSize, long requestTimeoutMs) {
    return start(endpoint, queueSize, requestTimeoutMs, 1);
  }

  private AuditGatewayAppender start(String endpoint, int queueSize, long requestTimeoutMs, int workers) {
    appender = AuditGatewayAppender.createAppender("test", null, endpoint, EVENTS_PATH, "system:serviceaccount:knox:knox",
        "knox-0", queueSize, requestTimeoutMs, 2000L, workers);
    appender.start();
    return appender;
  }

  private void awaitArrived(int count) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000L;
    while (arrived.get() < count && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertTrue("expected " + count + " arrivals, got " + arrived.get(), arrived.get() >= count);
  }

  private static LogEvent sample() {
    return event(Action.AUTHENTICATION, ResourceType.PRINCIPAL, "alice", ActionOutcome.SUCCESS)
        .users("alice", null, null).build();
  }

  private void awaitReceived(int count) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000L;
    while (received.size() < count && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertTrue("expected " + count + " requests, got " + received.size(), received.size() >= count);
  }

  @Test
  public void testResolveUri() {
    assertNull(AuditGatewayAppender.resolveUri(null, EVENTS_PATH));
    assertNull(AuditGatewayAppender.resolveUri("", EVENTS_PATH));
    assertNull(AuditGatewayAppender.resolveUri("  ", EVENTS_PATH));
    assertEquals(URI.create("http://audit-gateway.observability.svc.cluster.local:4319/api/v1/audit/events"),
        AuditGatewayAppender.resolveUri("audit-gateway.observability.svc.cluster.local:4319", EVENTS_PATH));
    assertEquals(URI.create("https://gw:4319/api/v1/audit/events"),
        AuditGatewayAppender.resolveUri("https://gw:4319/", EVENTS_PATH));
    assertEquals(URI.create("http://gw:4319/v2/events"), AuditGatewayAppender.resolveUri("gw:4319", "v2/events"));
    assertEquals(URI.create("http://gw:4319/custom"), AuditGatewayAppender.resolveUri("http://gw:4319/custom", null));
    assertEquals(URI.create("http://gw:4319/custom"), AuditGatewayAppender.resolveUri("http://gw:4319/custom", " "));
  }

  @Test(timeout = 20_000L)
  public void testPostsEvent() throws Exception {
    start(endpoint(), 10).append(sample());
    awaitReceived(1);
    Received r = received.get(0);
    assertEquals("POST", r.method);
    assertEquals(EVENTS_PATH, r.path);
    assertEquals("application/json", r.contentType);
    assertEquals("alice", MAPPER.readTree(r.body).get("sub").asText());
  }

  @Test(timeout = 20_000L)
  public void testFailedDeliveryIsNotRetried() throws Exception {
    statuses.add(503);
    statuses.add(400);
    AuditGatewayAppender a = start(endpoint(), 10);
    a.append(sample());
    a.append(sample());
    a.append(sample());
    awaitReceived(3);
    Thread.sleep(300);
    assertEquals(3, received.size());
    String first = MAPPER.readTree(received.get(0).body).get("event_id").asText();
    assertFalse(first.equals(MAPPER.readTree(received.get(1).body).get("event_id").asText()));
  }

  @Test(timeout = 30_000L)
  public void testWorkersSendInParallel() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    gate.set(latch);
    AuditGatewayAppender a = start(endpoint(), 10, 20_000L, 4);
    for (int i = 0; i < 6; i++) {
      a.append(sample());
    }
    awaitArrived(4);
    Thread.sleep(200);
    assertEquals(4, arrived.get());
    latch.countDown();
    awaitReceived(6);
    a.stop(2, TimeUnit.SECONDS);
    appender = null;
    assertEquals(6, received.size());
  }

  @Test
  public void testWorkerCountIsClamped() {
    assertEquals(1, AuditGatewayAppender.createAppender("w", null, "gw:1", EVENTS_PATH, "s", null, 10, 1000L, 1000L, 0)
        .getWorkerCount());
    assertEquals(AuditGatewayAppender.MAX_WORKERS, AuditGatewayAppender.createAppender("w", null, "gw:1", EVENTS_PATH, "s",
        null, 10, 1000L, 1000L, 25).getWorkerCount());
    assertEquals(10, AuditGatewayAppender.createAppender("w", null, "gw:1", EVENTS_PATH, "s", null, 10, 1000L, 1000L, 10)
        .getWorkerCount());
  }

  @Test(timeout = 20_000L)
  public void testBlankEndpointSendsNothing() throws Exception {
    AuditGatewayAppender a = start("", 10);
    assertFalse(a.isEnabled());
    a.append(sample());
    Thread.sleep(300);
    assertTrue(received.isEmpty());
  }

  @Test(timeout = 30_000L)
  public void testFullQueueDropsWithoutBlocking() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    gate.set(latch);
    AuditGatewayAppender a = start(endpoint(), 2, 20_000L);
    a.append(sample());
    awaitArrived(1);
    long begin = System.currentTimeMillis();
    for (int i = 0; i < 49; i++) {
      a.append(sample());
    }
    assertTrue(System.currentTimeMillis() - begin < 2000L);
    assertEquals(47, a.getDropped());
    latch.countDown();
    awaitReceived(3);
  }

  @Test(timeout = 20_000L)
  public void testStopHonoursTimeoutAgainstHungEndpoint() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    gate.set(latch);
    AuditGatewayAppender a = start(endpoint(), 10, 15_000L);
    for (int i = 0; i < 5; i++) {
      a.append(sample());
    }
    awaitArrived(1);
    long begin = System.currentTimeMillis();
    a.stop(500, TimeUnit.MILLISECONDS);
    long elapsed = System.currentTimeMillis() - begin;
    appender = null;
    assertTrue("stop took " + elapsed + " ms", elapsed < 2000L);
    assertTrue(a.isStopped());
  }

  @Test(timeout = 20_000L)
  public void testStopDrainsQueue() throws Exception {
    AuditGatewayAppender a = start(endpoint(), 10);
    for (int i = 0; i < 5; i++) {
      a.append(sample());
    }
    a.stop(1, TimeUnit.SECONDS);
    appender = null;
    assertEquals(5, received.size());
  }
}
