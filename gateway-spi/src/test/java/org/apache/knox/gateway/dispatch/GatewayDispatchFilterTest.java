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
package org.apache.knox.gateway.dispatch;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;


public class GatewayDispatchFilterTest {

  /*
   * Verify that a whitelist violation results in a HTTP 400 response.
   */
  @Test
  public void testServiceDispatchWhitelistViolation() throws Exception {
    final String serviceRole = "KNOXSSO";

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDispatchWhitelistServices()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.expect(config.getDispatchWhitelist()).andReturn(null).anyTimes();
    EasyMock.replay(config);

    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute("org.apache.knox.gateway.config")).andReturn(config).anyTimes();
    EasyMock.replay(sc);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getMethod()).andReturn("GET").anyTimes();
    EasyMock.expect(request.getServerName()).andReturn("localhost").anyTimes();
    EasyMock.expect(request.getRequestURI()).andReturn("http://www.notonmylist.org:9999").anyTimes();
    EasyMock.expect(request.getAttribute("targetServiceRole")).andReturn(serviceRole).anyTimes();
    EasyMock.expect(request.getServletContext()).andReturn(sc).anyTimes();
    EasyMock.replay(request);

    HttpServletResponse response = new TestHttpServletResponse();
    (new GatewayDispatchFilter()).doFilter(request, response, null);
    assertEquals(400, response.getStatus());
  }


  /*
   * If the dispatch service is not configured to honor the whitelist, the dispatching should be permitted.
   */
  @Test
  public void testServiceDispatchWhitelistNoServiceRoles() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.emptyList(),
                                   "^https?://localhost.*$",
                                   serviceRole,
                                   "http://www.notonmylist.org:9999", true);
  }

  /*
   * If the dispatch service is configured to honor the whitelist, but no whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does not match the default whitelist, then the dispatch should be
   * disallowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_invalid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   null,
                                   serviceRole,
                                   "http://www.notonmylist.org:9999",
                                   false);
  }

  /*
   * If the dispatch service is configured to honor the whitelist, but no whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does not match the default whitelist, then the dispatch should be
   * disallowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_invalid_alt_localhost() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "127.0.0.1",
                                   null,
                                   serviceRole,
                                   "http://www.notonmylist.org:9999", false);
  }

  /*
   * If the dispatch service is configured to honor the whitelist, but no whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does not match the default domain-based whitelist, then the
   * dispatch should be disallowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_invalid_domain() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "knoxbox.test.org",
                                   null,
                                   serviceRole,
                                   "http://www.notonmylist.org:9999",
                                   false);
  }

  /*
   * If the dispatch service is configured to honor the whitelist, but no whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does match the default whitelist, then the dispatch should be
   * allowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_valid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   null,
                                   serviceRole,
                                   "http://localhost:9999",
                                   true);
  }

  /*
   * If the dispatch service is configured to honor the whitelist, but no whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does match the default whitelist, then the dispatch should be
   * allowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_encodedurl_valid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   null,
                                   serviceRole,
                                   URLEncoder.encode("http://localhost:9999", StandardCharsets.UTF_8.name()),
                                   true);
  }


  /*
   * If the dispatch service is configured to honor the whitelist, but DEFAULT whitelist is configured, then the default
   * whitelist should be applied. If the dispatch URL does match the default whitelist, then the dispatch should be
   * allowed.
   */
  @Test
  public void testServiceDispatchWhitelistNoWhiteListForRole_encodedurl_invalid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "DEFAULT",
                                   serviceRole,
                                   URLEncoder.encode("http://www.notonmylist.org:9999", StandardCharsets.UTF_8.name()),
                                   false);
  }


  /*
   * An empty whitelist should be treated as the default whitelist.
   */
  @Test
  public void testServiceDispatchWhitelistEmptyWhitelist() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "",
                                   serviceRole,
                                   "http://www.notonmylist.org:9999",
                                   false); // Should be disallowed because nothing can match an empty whitelist
  }


  /*
   * If a custom whitelist is configured, and the requested service role is among those configured to honor that
   * whitelist, the request should be disallowed if the URL does NOT match the whitelist.
   */
  @Test
  public void testServiceDispatchWhitelistCustomWhitelist_invalid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "^.*mydomain\\.org.*$;^.*myotherdomain.com.*",
                                   serviceRole,
                                   "http://www.notonmylist.org:9999",
                                   false);
  }


  /*
   * If a custom whitelist is configured, and the requested service role is among those configured to honor that
   * whitelist, the request should be permitted if the URL matches the whitelist.
   */
  @Test
  public void testServiceDispatchWhitelistCustomWhitelist_valid() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
                                   "^.*mydomain\\.org.*$;^.*myotherdomain.com.*;^.*onmylist\\.org.*$",
                                   serviceRole,
                                   "http://www.onmylist.org:9999",
                                   true);
  }

  /*
   * The configured whitelist should be ignored for services which are NOT configured to honor the whitelist, and those
   * dispatches should be permitted.
   */
  @Test
  public void testServiceDispatchWhitelistCustomWhitelistNoServiceRoles() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Arrays.asList("MYROLE","SOMEOTHER_ROLE"), // Different roles than the one requested
                                   "^.*mydomain\\.org.*$;^.*myotherdomain.com.*",
                                   serviceRole,
                                   "http://www.onmylist.org:9999",
                                    true);
  }

  @Test
  public void testIgnorePathSegmentsAndQueryParams() throws Exception {
    final String serviceRole = "TESTROLE";
    doTestServiceDispatchWhitelist(Collections.singletonList(serviceRole),
            "^https://localhost:[0-9]+/?$",
            serviceRole,
            "https://localhost:1234/any/path?any=query",
            true);
  }

  private void doTestServiceDispatchWhitelist(List<String> whitelistedServices,
                                              String       whitelist,
                                              String       serviceRole,
                                              String       dispatchURL,
                                              boolean      expectation) throws Exception {
    doTestServiceDispatchWhitelist(whitelistedServices, "localhost", whitelist, serviceRole, dispatchURL, expectation);
  }

  private void doTestServiceDispatchWhitelist(List<String> whitelistedServices,
                                              String       serverName,
                                              String       whitelist,
                                              String       serviceRole,
                                              String       dispatchURL,
                                              boolean      expectation) throws Exception {

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getDispatchWhitelistServices()).andReturn(whitelistedServices).anyTimes();
    EasyMock.expect(config.getDispatchWhitelist()).andReturn(whitelist).anyTimes();
    EasyMock.replay(config);

    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute("org.apache.knox.gateway.config")).andReturn(config).anyTimes();
    EasyMock.replay(sc);

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServerName()).andReturn(serverName).anyTimes();
    EasyMock.expect(request.getRequestURI()).andReturn(dispatchURL).anyTimes();
    EasyMock.expect(request.getAttribute("targetServiceRole")).andReturn(serviceRole).anyTimes();
    EasyMock.expect(request.getServletContext()).andReturn(sc).anyTimes();
    EasyMock.replay(request);

    GatewayDispatchFilter gdf = new GatewayDispatchFilter();
    Method isDispatchAllowedMethod =
        GatewayDispatchFilter.class.getDeclaredMethod("isDispatchAllowed", HttpServletRequest.class);
    isDispatchAllowedMethod.setAccessible(true);
    boolean isAllowed = (boolean) isDispatchAllowedMethod.invoke(gdf, request);
    assertEquals(expectation, isAllowed);
  }

  private static class TestHttpServletResponse extends MockHttpServletResponse {
    int status;

    @Override
    public void sendError(int i) throws IOException {
      status = i;
    }

    @Override
    public int getStatus() {
      return status;
    }
  }
}
