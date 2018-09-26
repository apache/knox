/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WhitelistUtilsTest {

  private static final List<String> LOCALHOST_NAMES = Arrays.asList("localhost", "127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

  @Test
  public void testDefault() throws Exception {
    String whitelist = doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.emptyList(), null), "TEST");
    assertNull("The test service role is not configured to honor the whitelist, so there should be none returned.",
               whitelist);
  }

  /**
   * KNOXSSO is implicitly included in the set of service roles for which the whitelist will be applied.
   */
  @Test
  public void testDefaultKnoxSSO() throws Exception {
    String whitelist = doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.emptyList(), null), "KNOXSSO");
    assertNotNull(whitelist);
  }

  @Test
  public void testDefaultForAffectedServiceRole() throws Exception {
    final String serviceRole = "TEST";

    GatewayConfig config = createMockGatewayConfig(Collections.singletonList(serviceRole), null);

    // Check localhost by name
    String whitelist = doTestGetDispatchWhitelist(config, serviceRole);
    assertNotNull(whitelist);
    assertTrue("Expected whitelist to contain 'localhost' but was: " + whitelist, whitelist.contains("localhost"));

    // Check localhost by loopback address
    whitelist = doTestGetDispatchWhitelist(config, "127.0.0.1", serviceRole);
    assertNotNull(whitelist);
    assertTrue("Expected whitelist to contain 'localhost' but was: " + whitelist, whitelist.contains("localhost"));
  }

  @Test
  public void testDefaultDomainWhitelist() throws Exception {
    final String serviceRole = "TEST";

    String whitelist =
                doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), null),
                                           "host0.test.org",
                                           serviceRole);
    assertNotNull(whitelist);
    assertTrue(whitelist.contains("\\.test\\.org"));
  }

  @Test
  public void testDefaultDomainWhitelistLocalhostDisallowed() throws Exception {
    String whitelist = doTestDeriveDomainBasedWhitelist("host.test.org");
    assertNotNull(whitelist);
    // localhost names should be excluded from the whitelist when the Knox host domain can be determined
    for (String name : LOCALHOST_NAMES) {
      assertFalse(RegExUtils.checkWhitelist(whitelist, name));
    }
  }

  @Test
  public void testDefaultDomainWhitelistWithXForwardedHost() throws Exception {
    final String serviceRole = "TEST";

    String whitelist =
        doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), null),
                                   "host0.test.org",
                                   "lb.external.test.org",
                                   serviceRole);
    assertNotNull(whitelist);
    assertTrue(whitelist.contains("\\.external\\.test\\.org"));
  }

  @Test
  public void testDefaultDomainWhitelistWithXForwardedHostAndPort() throws Exception {
    final String serviceRole = "TEST";

    String whitelist =
        doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), null),
                                   "host0.test.org",
                                   "lb.external.test.org:9090",
                                   serviceRole);
    assertNotNull(whitelist);
    assertTrue(whitelist.contains("\\.external\\.test\\.org"));
    assertFalse(whitelist.contains("9090"));
  }

  @Test
  public void testConfiguredWhitelist() throws Exception {
    final String serviceRole = "TEST";
    final String WHITELIST   = "^.*\\.my\\.domain\\.com.*$";

    String whitelist =
                doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), WHITELIST),
                                           serviceRole);
    assertNotNull(whitelist);
    assertTrue(whitelist.equals(WHITELIST));
  }

  @Test
  public void testLocalhostAddressAsHostName() throws Exception {
    final String serviceRole = "TEST";
    // InetAddress#getCanonicalHostName() sometimes returns the IP address as the host name
    String whitelist = doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), null),
                                                  "192.168.1.100",
                                                  serviceRole);
    assertNotNull(whitelist);
    assertTrue(whitelist.contains("192.168.1.100"));
  }

  @Test
  public void testExplicitlyConfiguredDefaultWhitelist() throws Exception {
    final String serviceRole = "TEST";
    final String WHITELIST   = "DEFAULT";

    String whitelist =
        doTestGetDispatchWhitelist(createMockGatewayConfig(Collections.singletonList(serviceRole), WHITELIST),
                                   serviceRole);
    assertNotNull(whitelist);
    assertTrue("Expected to match whitelist given the explicitly configured DEFAULT whitelist.",
        RegExUtils.checkWhitelist(whitelist, "http://localhost:9099/"));
  }

  private String doTestGetDispatchWhitelist(GatewayConfig config, String serviceRole) {
    return doTestGetDispatchWhitelist(config, "localhost", serviceRole);
  }


  private String doTestGetDispatchWhitelist(GatewayConfig config,
                                            String        serverName,
                                            String        serviceRole) {
    return doTestGetDispatchWhitelist(config, serverName, null, serviceRole);
  }

  private String doTestGetDispatchWhitelist(GatewayConfig config,
                                            String        serverName,
                                            String        xForwardedHost,
                                            String        serviceRole) {
    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute("org.apache.knox.gateway.config")).andReturn(config).anyTimes();
    EasyMock.replay(sc);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
      EasyMock.expect(request.getHeader("X-Forwarded-Host")).andReturn(xForwardedHost).anyTimes();
    }
    EasyMock.expect(request.getAttribute("targetServiceRole")).andReturn(serviceRole).anyTimes();
    EasyMock.expect(request.getServletContext()).andReturn(sc).anyTimes();
    EasyMock.expect(request.getServerName()).andReturn(serverName).anyTimes();
    EasyMock.replay(request);

    String result = null;
//    if (serverName != null && !serverName.isEmpty() && !isLocalhostServerName(serverName) && xForwardedHost == null) {
//      try {
//        result = doTestDeriveDomainBasedWhitelist(serverName);
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//    } else if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
    if (xForwardedHost != null && !xForwardedHost.isEmpty()) {
      try {
        Method method = WhitelistUtils.class.getDeclaredMethod("deriveDefaultDispatchWhitelist", HttpServletRequest.class);
        method.setAccessible(true);
        result = (String) method.invoke(null, request);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      result = WhitelistUtils.getDispatchWhitelist(request);
    }

    return result;
  }

  private static String doTestDeriveDomainBasedWhitelist(final String serverName) throws Exception {
    // First, need to invoke the method for deriving the domain from the server name
    Method getDomainMethod = WhitelistUtils.class.getDeclaredMethod("getDomain", String.class);
    getDomainMethod.setAccessible(true);
    String domain = (String) getDomainMethod.invoke(null, serverName);

    // Then, invoke the method for defining the whitelist based on the domain we just derived (which may be invalid)
    Method defineWhitelistMethod = WhitelistUtils.class.getDeclaredMethod("defineWhitelistForDomain", String.class);
    defineWhitelistMethod.setAccessible(true);
    return (String) defineWhitelistMethod.invoke(null, domain);
  }

  private static boolean isLocalhostServerName(final String serverName) {
    return LOCALHOST_NAMES.contains(serverName.toLowerCase(Locale.ROOT));
  }

  private static GatewayConfig createMockGatewayConfig(final List<String> serviceRoles, final String whitelist) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDispatchWhitelistServices()).andReturn(serviceRoles).anyTimes();
    EasyMock.expect(config.getDispatchWhitelist()).andReturn(whitelist).anyTimes();
    EasyMock.replay(config);

    return config;
  }

}
