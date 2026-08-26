/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.trace;

import org.apache.knox.test.log.CollectAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;

public class TraceHandlerTest {

  public static final int SEND_TIMEOUT_IN_MS = 5000;
  public static final long POLLING_TIMEOUT = 5000;
  public static final long POLLING_DELAY = 200;

  private Server server;

  private static Server buildServer(Handler inner) throws Exception {
    Server s = new Server();
    ServerConnector connector = new ServerConnector(s);
    connector.setPort(0);
    s.addConnector(connector);

    TraceHandler traceHandler = new TraceHandler();
    traceHandler.setHandler(inner);
    s.setHandler(traceHandler);
    return s;
  }

  private static void setTraceLoggersLevel(Level level) {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    for (String name : new String[]{
    TraceHandler.HTTP_REQUEST_LOGGER,
    TraceHandler.HTTP_REQUEST_HEADER_LOGGER,
    TraceHandler.HTTP_REQUEST_BODY_LOGGER,
    TraceHandler.HTTP_RESPONSE_LOGGER,
    TraceHandler.HTTP_RESPONSE_HEADER_LOGGER,
    TraceHandler.HTTP_RESPONSE_BODY_LOGGER
    }) {
      LoggerConfig lc = config.getLoggerConfig(name);
      if (lc.getName().equals(name)) {
        lc.setLevel(level);
      } else {
        config.addLogger(name, new LoggerConfig(name, level, true));
      }
    }
    ctx.updateLoggers();
  }

  private static HttpTester.Response send(URI serverURI, String rawRequest) throws Exception {
    try (Socket socket = new Socket(serverURI.getHost(), serverURI.getPort());
         OutputStream out = socket.getOutputStream();
         InputStream in = socket.getInputStream()) {
      socket.setSoTimeout(SEND_TIMEOUT_IN_MS);
      out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
      out.flush();
      return HttpTester.parseResponse(in);
    }
  }

  private static List<String> loggedMessages() {
    return CollectAppender.queue.stream()
    .map(e -> e.getMessage().getFormattedMessage())
    .collect(Collectors.toList());
  }

  private static List<String> awaitLoggedMessages(int expectedCount) throws InterruptedException {
    long maxTime = System.currentTimeMillis() + POLLING_TIMEOUT;
    do {
      if (CollectAppender.queue.size() >= expectedCount) {
        return loggedMessages();
      }
      TimeUnit.MILLISECONDS.sleep(POLLING_DELAY);
    } while (System.currentTimeMillis() < maxTime);

    throw new IllegalStateException("Timed out " + POLLING_TIMEOUT + "ms waiting for " + expectedCount +
      " log messages.");
  }

  @Before
  public void setUp() {
    CollectAppender.queue.clear();
  }

  @After
  public void tearDown() {
    LifeCycle.stop(server);
    setTraceLoggersLevel(Level.OFF);
  }

  @Test
  public void testNormalResponseLogsRequestAndResponseAndBody() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(200);
        Content.Sink.write(response, true, "hello world", callback);
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.TRACE);

    URI uri = server.getURI();
    String rawRequest = "GET /test HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    HttpTester.Response response = send(uri, rawRequest);

    // Wait for at least 3 logs (Request line, Response line, Body)
    List<String> messages = awaitLoggedMessages(3);

    assertThat(response.getStatus(), is(200));

    long requestLines = messages.stream().filter(m -> m.contains("|Request=GET")).count();
    long responseLines = messages.stream().filter(m -> m.contains("|Response=200")).count();
    long bodyLines = messages.stream().filter(m -> m.contains("|ResponseBody[")).count();

    assertThat("request line logged once", requestLines, is(1L));
    assertThat("response line logged once", responseLines, is(1L));
    assertThat("response body logged once", bodyLines, is(1L));

    String bodyLog = messages.stream().filter(m -> m.contains("|ResponseBody[")).findFirst().orElse("");
    assertThat(bodyLog, containsString("hello world"));
  }

  @Test
  public void testNoBodyResponseStillLogsResponseLine() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(204);
        response.write(true, BufferUtil.EMPTY_BUFFER, callback);
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.TRACE);

    URI uri = server.getURI();
    String rawRequest = "GET /empty HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    HttpTester.Response response = send(uri, rawRequest);

    // Wait for at least 2 logs (Request line, Response line)
    List<String> messages = awaitLoggedMessages(2);

    assertThat(response.getStatus(), is(204));

    long responseLines = messages.stream().filter(m -> m.contains("|Response=204")).count();
    assertThat("response line logged once for 204", responseLines, is(1L));
  }

  @Test
  public void testTraceOffProducesNoLogEvents() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(200);
        Content.Sink.write(response, true, "silent", callback);
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.OFF);

    URI uri = server.getURI();
    String rawRequest = "GET /silent HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    send(uri, rawRequest);

    // Yield to give Jetty a chance to log something before we assert
    TimeUnit.MILLISECONDS.sleep(POLLING_TIMEOUT);

    assertThat("no log events when TRACE is off", loggedMessages(), is(empty()));
  }

  @Test
  public void testBodyLoggerOffSuppressesBodyOnly() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(200);
        Content.Sink.write(response, true, "secret body", callback);
        return true;
      }
    });
    server.start();

    // enable request/response line loggers but leave body loggers OFF
    setTraceLoggersLevel(Level.TRACE);
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    Configuration config = ctx.getConfiguration();
    config.getLoggerConfig(TraceHandler.HTTP_REQUEST_BODY_LOGGER).setLevel(Level.OFF);
    config.getLoggerConfig(TraceHandler.HTTP_RESPONSE_BODY_LOGGER).setLevel(Level.OFF);
    ctx.updateLoggers();

    URI uri = server.getURI();
    String rawRequest = "GET /body-off HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    send(uri, rawRequest);

    // Wait for 2 logs (Request line, Response line)
    List<String> messages = awaitLoggedMessages(2);

    long requestLines  = messages.stream().filter(m -> m.contains("|Request=GET")).count();
    long responseLines = messages.stream().filter(m -> m.contains("|Response=200")).count();
    long bodyLines     = messages.stream().filter(m -> m.contains("Body[")).count();

    assertThat("request line logged", requestLines, is(1L));
    assertThat("response line logged", responseLines, is(1L));
    assertThat("no body logged when body logger is OFF", bodyLines, is(0L));
  }

  @Test
  public void testResponseStatusReflectsActualHandlerStatus() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(404);
        Content.Sink.write(response, true, "not found", callback);
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.TRACE);

    URI uri = server.getURI();
    String rawRequest = "GET /missing HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    HttpTester.Response response = send(uri, rawRequest);

    // Wait for 2 logs (Request line, Response line)
    List<String> messages = awaitLoggedMessages(2);

    assertThat(response.getStatus(), is(404));

    assertThat("logged status is 404", messages.stream().anyMatch(m -> m.contains("|Response=404")), is(true));
    assertThat("logged status is NOT 0 or 200",
    messages.stream().noneMatch(m -> m.contains("|Response=0") || m.contains("|Response=200")), is(true));
  }

  @Test
  public void testRequestBodyIsLogged() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) throws Exception {
        Content.Source.consumeAll(request, Callback.from(() -> {
          response.setStatus(200);
          response.write(true, BufferUtil.EMPTY_BUFFER, callback);
        }, callback::failed));
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.TRACE);

    URI uri = server.getURI();
    String body = "request payload";
    String rawRequest = "POST /upload HTTP/1.1\r\n" +
    "Host: " + uri.getRawAuthority() + "\r\n" +
    "Content-Length: " + body.length() + "\r\n" +
    "Connection: close\r\n\r\n" +
    body;
    send(uri, rawRequest);

    // Wait for 3 logs (Request line, Request body, Response line)
    List<String> messages = awaitLoggedMessages(3);

    long requestBodyLines = messages.stream().filter(m -> m.contains("|RequestBody[")).count();
    assertThat("request body logged once", requestBodyLines, is(1L));

    String bodyLog = messages.stream().filter(m -> m.contains("|RequestBody[")).findFirst().orElse("");
    assertThat(bodyLog, containsString("request payload"));
  }

  @Test
  public void testResponseLineLoggedOnceAcrossMultipleWrites() throws Exception {
    server = buildServer(new Handler.Abstract() {
      @Override
      public boolean handle(Request request, Response response, Callback callback) {
        response.setStatus(200);
        response.write(false, StandardCharsets.UTF_8.encode("chunk1"),
        Callback.from(() -> response.write(true, StandardCharsets.UTF_8.encode("chunk2"), callback),
        callback::failed));
        return true;
      }
    });
    server.start();
    setTraceLoggersLevel(Level.TRACE);

    URI uri = server.getURI();
    String rawRequest = "GET /chunked HTTP/1.1\r\nHost: " + uri.getRawAuthority() + "\r\nConnection: close\r\n\r\n";
    send(uri, rawRequest);

    // Wait for 2 logs (Request line, Response line)
    List<String> messages = awaitLoggedMessages(2);

    long responseLines = messages.stream().filter(m -> m.contains("|Response=")).count();
    assertThat("response line logged exactly once across multiple writes", responseLines, is(1L));
  }
}
