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
package org.apache.knox.gateway.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.Test;

import javax.security.auth.Subject;
import java.io.IOException;
import java.net.ServerSocket;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class KnoxSessionTest {
  public static final String PEM = "MIICOjCCAaOgAwIBAgIJAN5kp1oW3Up8MA0GCSqGSIb3DQEBBQUAMF8xCzAJBgNVBAYTAlVTMQ0w\n"
      + "CwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZIYWRvb3AxDTALBgNVBAsTBFRl\n"
      + "c3QxEjAQBgNVBAMTCWxvY2FsaG9zdDAeFw0xODEyMTMwMzE2MTFaFw0xOTEyMTMwMzE2MTFaMF8x\n"
      + "CzAJBgNVBAYTAlVTMQ0wCwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZIYWRv\n"
      + "b3AxDTALBgNVBAsTBFRlc3QxEjAQBgNVBAMTCWxvY2FsaG9zdDCBnzANBgkqhkiG9w0BAQEFAAOB\n"
      + "jQAwgYkCgYEAqxnzKNhNgPEeOWsTabaxR9N3QjKohvDOrAwwVvzVhHIb1GKRo+TfSkDozS3BmzuO\n"
      + "+xQN6LvIsE6pzl+TFvTJvM9Ir5vMyybww8ZVkeD7vaHvBT9+w+1R79wYEhC7kqj68bGJJpl+1fGa\n"
      + "c6yTKBYcAs3hO54Zg56rgreQKwXeBysCAwEAATANBgkqhkiG9w0BAQUFAAOBgQACFpBmy7KgSiBG\n"
      + "0flF1+l8KXCU7t3LL8F3RlJSF4fyexfojilkHW7u6TdJbrAsz5nhe85AchFl6/jtmvCMGMFPobMI\n"
      + "f/44w9sYdC3u604wJy8CF5xKqDb/en4xmiLnEc0LzOeEvtFv0ociu82SuRara7ua1J6UR9JsNu5p\n"
      + "dWEFEA==\n";

  @Test
  public void testParsingPublicCertPem() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    context.connection().withPublicCertPem(PEM);
    KnoxSession session = KnoxSession.login(context);
    session.close();
  }

  @Test
  public void testParsingInvalidPublicCertPem() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    try {
      context.connection().withPublicCertPem("INVLID-" + PEM);
      KnoxSession session = KnoxSession.login(context);
      fail("Invalid Public Cert should have resulted in CertificateException wrapped by KnoxShellException");
      session.close();
    }
    catch (KnoxShellException e) {
      assertTrue(e.getCause().toString().contains("CertificateException"));
    }
  }

  @Test
  public void testParsingPublicCertPemWithCertDelimiters() throws Exception {

    final ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt");
    try {
      context.connection().withPublicCertPem(KnoxSession.BEGIN_CERTIFICATE + PEM + KnoxSession.END_CERTIFICATE);
      KnoxSession session = KnoxSession.login(context);
      session.close();
    }
    catch (KnoxShellException e) {
      fail("Should have been able to parse cert with BEGIN and END Certificate delimiters.");
    }
  }

  /**
   * Validate that the jaasConf option is applied when specified for a kerberos KnoxSession login.
   */
  @Test
  public void testJAASConfigOption() {
    final String testJaasConf = "/etc/knoxsessiontest-jaas.conf";

    final Logger logger = Logger.getLogger("org.apache.knox.gateway.shell");
    final Level originalLevel = logger.getLevel();
    logger.setLevel(Level.FINEST);
    LogHandler logCapture = new LogHandler();
    logger.addHandler(logCapture);

    try {
      ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt")
                                           .kerberos()
                                           .enable(true)
                                           .jaasConf(testJaasConf)
                                           .end();
      assertNotNull(context);
      assertEquals(context.kerberos().jaasConf(), testJaasConf);

      try {
        KnoxSession.login(context).executeNow(null);
      } catch (Exception e) {
        // Expected because the HTTP request is null, which is irrelevant for this test
      }

      assertFalse(logCapture.logMessages.isEmpty());
      assertEquals("The specified JAAS configuration does not exist: " + testJaasConf, logCapture.logMessages.get(0));
      assertEquals("Using default JAAS configuration", logCapture.logMessages.get(1));
      assertTrue(logCapture.logMessages.get(2).startsWith("JAAS configuration: "));
      assertTrue(logCapture.logMessages.get(2).endsWith("jaas.conf"));
      assertEquals("No available Subject; Using JAAS configuration login", logCapture.logMessages.get(3));
      assertEquals("Using JAAS configuration file implementation: com.sun.security.auth.login.ConfigFile",
                   logCapture.logMessages.get(4));
    } finally {
      logger.removeHandler(logCapture);
      logger.setLevel(originalLevel);
    }
  }

  /**
   * Validate that JAAS configuration is not applied when a kerberos Subject is available.
   * (KNOX-1850)
   */
  @Test
  public void testUseCurrentSubject() {
    final Logger logger = Logger.getLogger("org.apache.knox.gateway.shell");
    final Level originalLevel = logger.getLevel();
    logger.setLevel(Level.FINEST);
    LogHandler logCapture = new LogHandler();
    logger.addHandler(logCapture);

    try {
      ClientContext context = ClientContext.with("https://localhost:8443/gateway/dt")
                                           .kerberos()
                                           .enable(true)
                                           .end();
      assertNotNull(context);

      Subject testSubject = new Subject();

      try {
        KnoxSession session = KnoxSession.login(context);
        Subject.doAs(testSubject, (PrivilegedAction<CloseableHttpResponse>) () -> {
          try {
            return session.executeNow(null);
          } catch (IOException e) {
            e.printStackTrace();
          }
          return null;
        });
      } catch (Exception e) {
        // Expected because the HTTP request is null, which is irrelevant for this test
      }

      if(!logCapture.logMessages.isEmpty()) {
        for (String logMessage : logCapture.logMessages) {
          assertFalse(logMessage.startsWith("No available Subject"));
        }
      }
    } finally {
      logger.removeHandler(logCapture);
      logger.setLevel(originalLevel);
    }
  }

  @Test
  public void testRetry() throws Exception {
    int[] counter = new int[]{ 0 };
    HttpServer server = ServerBootstrap.bootstrap()
            .setListenerPort(findFreePort())
            .registerHandler("/retry", (req, resp, ctx) -> resp.setStatusCode(counter[0]++ < 2 ? 503 : 200))
            .create();
    server.start();
    try {
      ClientContext context = ClientContext.with("http://localhost:" + server.getLocalPort());
      context.connection().retryCount(2);
      KnoxSession session = KnoxSession.login(context);
      assertEquals(200, session.executeNow(new HttpGet("/retry")).getStatusLine().getStatusCode());
      session.close();
      assertEquals(3, counter[0]);
    } finally {
      server.stop();
    }
  }

  public static int findFreePort() throws IOException {
    try(ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static class LogHandler extends Handler {

    List<String> logMessages = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      logMessages.add(record.getMessage());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
    }
  }

}
