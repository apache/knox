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
package org.apache.knox.gateway.dispatch;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HadoopAuthCookieStoreTest {

  /**
   * Test for the issue reported as KNOX-1171
   * Tests the required to workaround Oozie 4.3/Hadoop 2.4 not properly formatting the hadoop.auth cookie.
   * See the following jiras for additional context:
   *   https://issues.apache.org/jira/browse/HADOOP-10710
   *   https://issues.apache.org/jira/browse/HADOOP-10379
   */
  @Test
  public void testOozieCookieWorkaroundKnox1171() {
    String rawValue = "u=knox&p=knox/host.example.com.com@EXAMPLE.COM&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=";
    String quotedValue = "\""+rawValue+"\"";

    HadoopAuthCookieStore store;
    List<Cookie> cookies;
    Cookie cookie;

    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    File krb5LoginConf = createTestKrb5LoginConfigFile("krb5JAASLogin",
                                                       getTestKrb5LoginConf("knox/host.example.com.com@EXAMPLE.COM"));
    assertNotNull(krb5LoginConf);
    EasyMock.expect(gatewayConfig.getKerberosLoginConfig()).andReturn(krb5LoginConf.getAbsolutePath()).anyTimes();
    EasyMock.replay(gatewayConfig);

    store = new HadoopAuthCookieStore(gatewayConfig);
    store.addCookie( new BasicClientCookie( "hadoop.auth", rawValue ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is(quotedValue) );

    store = new HadoopAuthCookieStore(gatewayConfig);
    store.addCookie( new BasicClientCookie( "hadoop.auth", quotedValue ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is(quotedValue) );

    store = new HadoopAuthCookieStore(gatewayConfig);
    store.addCookie( new BasicClientCookie( "hadoop.auth", null ) );
    cookies = store.getCookies();
    assertNotNull(cookies);
    assertTrue(cookies.isEmpty());

    store = new HadoopAuthCookieStore(gatewayConfig);
    store.addCookie( new BasicClientCookie( "hadoop.auth", "" ) );
    cookies = store.getCookies();
    assertNotNull(cookies);
    assertTrue(cookies.isEmpty());
  }

  @Test
  public void testKnoxCookieInclusionDefaultUserAndPrincipal() {
    doTestKnoxCookieInclusion("u=knox&p=knox/myhost.example.com@EXAMPLE.COM&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieInclusionDefaultUser() {
    doTestKnoxCookieExclusion("u=knox&p=anotherUser/myhost.example.com@EXAMPLE.COM&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieInclusionDefaultPrincipal() {
    doTestKnoxCookieInclusion("u=anotherUser&p=knox/myhost.example.com@EXAMPLE.COM&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieExclusionWrongUserAndPrincipal() {
    doTestKnoxCookieExclusion("u=test&p=dummy/host@EXAMPLE.COM&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieExclusionWrongUserNoPrincipal() {
    doTestKnoxCookieExclusion("u=test&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieInclusionDefaultUserAndCustomPrincipal() {
    final String principal = "myTestPrincipal/test@EXAMPLE.COM";

    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    File krb5LoginConf = createTestKrb5LoginConfigFile("krb5Login", getTestKrb5LoginConf(principal));
    assertNotNull(krb5LoginConf);
    EasyMock.expect(gatewayConfig.getKerberosLoginConfig()).andReturn(krb5LoginConf.getAbsolutePath()).anyTimes();
    EasyMock.replay(gatewayConfig);

    doTestKnoxCookieInclusion(gatewayConfig,
                              "u=knox&p=" + principal + "&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  @Test
  public void testKnoxCookieInclusionDefaultUserAndMissingPrincipal() {
    doTestKnoxCookieExclusion("u=knox&t=kerberos&e=1517900515610&s=HpSXUOhoXR/2wXrsgPz5lSbNuf8=");
  }

  private void doTestKnoxCookieInclusion(final String cookieValue) {
    GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    File krb5LoginConf = createTestKrb5LoginConfigFile();
    assertNotNull(krb5LoginConf);
    EasyMock.expect(gatewayConfig.getKerberosLoginConfig()).andReturn(krb5LoginConf.getAbsolutePath()).anyTimes();
    EasyMock.replay(gatewayConfig);

    doTestKnoxCookieInclusion(gatewayConfig, cookieValue);
  }

  private void doTestKnoxCookieInclusion(final GatewayConfig gatewayConfig, final String cookieValue) {
    HadoopAuthCookieStore store = new HadoopAuthCookieStore(gatewayConfig);
    store.addCookie(new BasicClientCookie("hadoop.auth", cookieValue));
    List<Cookie> cookies = store.getCookies();
    assertNotNull(cookies);
    assertFalse(cookies.isEmpty());
    assertThat(cookies.get(0).getValue(), is("\"" + cookieValue + "\""));
  }

  private void doTestKnoxCookieExclusion(final String cookieValue) {
    GatewayConfig gConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.replay(gConf);

    HadoopAuthCookieStore store = new HadoopAuthCookieStore(gConf);
    store.addCookie(new BasicClientCookie("hadoop.auth", cookieValue));
    List<Cookie> cookies = store.getCookies();
    assertNotNull(cookies);
    assertTrue(cookies.isEmpty());
  }

  private static File createTestKrb5LoginConfigFile() {
    return createTestKrb5LoginConfigFile("krb5JAASLogin", getTestKrb5LoginConf());
  }

  private static File createTestKrb5LoginConfigFile(String filename, String contents) {
    File result = null;
    try {
      File f = File.createTempFile(filename, ".conf");
      f.deleteOnExit();
      try(OutputStream out = Files.newOutputStream(f.toPath())) {
        out.write(contents.getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
      result = f;
    } catch (Exception e) {
      //
    }

    return result;
  }

  private static String getTestKrb5LoginConf() {
    return getTestKrb5LoginConf("knox/myhost.example.com@EXAMPLE.COM");
  }

  private static String getTestKrb5LoginConf(String principal) {
    return "com.sun.security.jgss.initiate {\n" +
           "com.sun.security.auth.module.Krb5LoginModule required\n" +
           "renewTGT=false\n" +
           "doNotPrompt=true\n" +
           "useKeyTab=true\n" +
           "keyTab=\"/etc/security/keytabs/knox.service.keytab\"\n" +
          (principal != null ? "principal=\"" + principal + "\"\n" : "") +
           "storeKey=true\n" +
           "useTicketCache=false;";
  }
}
