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
package org.apache.knox.gateway.provider.federation;

import static org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter.XHR_HEADER;
import static org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter.XHR_VALUE;
import static org.junit.Assert.fail;

import java.security.Principal;
import java.util.Properties;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSOCookieProviderTest extends AbstractJWTFilterTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(SSOCookieProviderTest.class);

  private static final String SERVICE_URL = "https://localhost:8888/resource";

  @Before
  public void setUp() {
    handler = new TestSSOCookieFederationProvider();
    ((TestSSOCookieFederationProvider) handler).setTokenService(new TestJWTokenAuthority(publicKey));
  }

  @Override
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    Cookie cookie1 = new Cookie("hadoop-jwt", "garbage");
    Cookie cookie2 = new Cookie("hadoop-jwt", jwt.serialize());
    EasyMock.expect(request.getCookies()).andReturn(new Cookie[] { cookie1, cookie2 });

    if(ThreadLocalRandom.current().nextBoolean()) {
      LOGGER.info("Using XHR header for request");
      EasyMock.expect(request.getHeader(XHR_HEADER)).andReturn(XHR_VALUE).anyTimes();
    }
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    Cookie cookie = new Cookie("hadoop-jwt", "ljm" + jwt.serialize());
    EasyMock.expect(request.getCookies()).andReturn(new Cookie[] { cookie });
  }

  @Override
  protected String getAudienceProperty() {
    return TestSSOCookieFederationProvider.SSO_EXPECTED_AUDIENCES;
  }

  @Test
  public void testCustomCookieNameJWT() throws Exception {
    try {
      Properties props = getProperties();
      props.put("sso.cookie.name", "jowt");
      handler.init(new TestFilterConfig(props));

      SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "alice",
                             new Date(new Date().getTime() + 5000), privateKey);

      Cookie cookie1 = new Cookie("jowt", "garbage");
      Cookie cookie2 = new Cookie("jowt", jwt.serialize());
      HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect(request.getCookies()).andReturn(new Cookie[] { cookie1, cookie2 });
      EasyMock.expect(request.getRequestURL()).andReturn(
          new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null);
      HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(
          SERVICE_URL);
      EasyMock.replay(request);

      TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);
      Assert.assertTrue("doFilterCalled should not be false.", chain.doFilterCalled );
      Set<PrimaryPrincipal> principals = chain.subject.getPrincipals(PrimaryPrincipal.class);
      Assert.assertFalse("No PrimaryPrincipal returned.", principals.isEmpty());
      Assert.assertEquals("Not the expected principal", "alice", ((Principal)principals.toArray()[0]).getName());
    } catch (ServletException se) {
      fail("Should NOT have thrown a ServletException.");
    }
  }

  @Test
  public void testNoProviderURLJWT() {
    try {
      Properties props = getProperties();
      props.remove("sso.authentication.provider.url");
      handler.init(new TestFilterConfig(props));
    } catch (ServletException se) {
      // no longer expected - let's ensure it mentions the missing authentication provider URL
      fail("Servlet exception should have been thrown.");
      se.printStackTrace();
    }
  }

  @Test
  public void testOrigURLWithQueryString() throws Exception {
    Properties props = getProperties();
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(
        new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn("name=value");
    EasyMock.replay(request);

    String loginURL = ((TestSSOCookieFederationProvider)handler).constructLoginURL(request);
    Assert.assertNotNull("loginURL should not be null.", loginURL);
    Assert.assertEquals("https://localhost:8443/authserver?originalUrl=" + SERVICE_URL + "?name=value", loginURL);
  }

  @Test
  public void testOrigURLNoQueryString() throws Exception {
    Properties props = getProperties();
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(
        new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);
    EasyMock.replay(request);

    String loginURL = ((TestSSOCookieFederationProvider)handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals("https://localhost:8443/authserver?originalUrl=" + SERVICE_URL, loginURL);
  }

  @Test
  public void testDefaultAuthenticationProviderURL() throws Exception {
    Properties props = new Properties();
    props.setProperty("gateway.path", "gateway");
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);
    EasyMock.expect(request.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect(request.getServerName()).andReturn("localhost").anyTimes();
    EasyMock.expect(request.getServerPort()).andReturn(8443).anyTimes();
    EasyMock.replay(request);

    String providerURL = ((TestSSOCookieFederationProvider) handler).deriveDefaultAuthenticationProviderUrl(request);
    Assert.assertNotNull("LoginURL should not be null.", providerURL);
    Assert.assertEquals(providerURL, "https://localhost:8888/gateway/knoxsso/api/v1/websso");

    String loginURL = ((TestSSOCookieFederationProvider) handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals(loginURL, "https://localhost:8888/gateway/knoxsso/api/v1/websso?originalUrl=" + SERVICE_URL);
  }

  @Test
  public void testProxiedDefaultAuthenticationProviderURL() throws Exception {
    // after KNOX-2467 enables jetty's xforwarded support this test has been
    // changed to expect the X-Forwarded Headers to be resolved by
    // httpRequest.getRequestURL instead of explicitly checking the headers
    // ourselves. Leaving the headers set to show this is a proxied request
    // but they really have no bearing on the results.
    Properties props = new Properties();
    props.setProperty("gateway.path", "gateway");
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer("https://remotehost:8443/resource")).anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PROTO)).andReturn("https").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_HOST)).andReturn("remotehost:8443").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PORT)).andReturn("8443").anyTimes();
    EasyMock.replay(request);

    String providerURL = ((TestSSOCookieFederationProvider) handler).deriveDefaultAuthenticationProviderUrl(request);
    Assert.assertNotNull("LoginURL should not be null.", providerURL);
    Assert.assertEquals(providerURL, "https://remotehost:8443/gateway/knoxsso/api/v1/websso");

    String loginURL = ((TestSSOCookieFederationProvider) handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals(loginURL, "https://remotehost:8443/gateway/knoxsso/api/v1/websso?originalUrl=" + "https://remotehost:8443/resource");
  }

  @Test
  public void testProxiedDefaultAuthenticationProviderURLWithoutNonGatewayAppPath() throws Exception {
    // after KNOX-2467 enables jetty's xforwarded support this test has been
    // changed to expect the X-Forwarded Headers to be resolved by
    // httpRequest.getRequestURL instead of explicitly checking the headers
    // ourselves. Leaving the headers set to show this is a proxied request
    // but they really have no bearing on the results.
    Properties props = new Properties();
    props.setProperty("gateway.path", "notgateway");
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer("https://remotehost:8443/resource")).anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PROTO)).andReturn("https").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_HOST)).andReturn("remotehost").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PORT)).andReturn("8443").anyTimes();
    EasyMock.replay(request);

    String providerURL = ((TestSSOCookieFederationProvider) handler).deriveDefaultAuthenticationProviderUrl(request);
    Assert.assertNotNull("LoginURL should not be null.", providerURL);
    Assert.assertEquals(providerURL, "https://remotehost:8443/notgateway/knoxsso/api/v1/websso");

    String loginURL = ((TestSSOCookieFederationProvider) handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals(loginURL, "https://remotehost:8443/notgateway/knoxsso/api/v1/websso?originalUrl=" + "https://remotehost:8443/resource");
  }

  @Test
  public void testProxiedDefaultAuthenticationProviderURLWithoutPortInHostHeader() throws Exception {
    // after KNOX-2467 enables jetty's xforwarded support this test has been
    // changed to expect the X-Forwarded Headers to be resolved by
    // httpRequest.getRequestURL instead of explicitly checking the headers
    // ourselves. Leaving the headers set to show this is a proxied request
    // but they really have no bearing on the results.
    Properties props = new Properties();
    props.setProperty("gateway.path", "notgateway");
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer("https://remotehost/resource")).anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PROTO)).andReturn("https").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_HOST)).andReturn("remotehost").anyTimes();
    EasyMock.replay(request);

    String providerURL = ((TestSSOCookieFederationProvider) handler).deriveDefaultAuthenticationProviderUrl(request);
    Assert.assertNotNull("LoginURL should not be null.", providerURL);
    Assert.assertEquals(providerURL, "https://remotehost/notgateway/knoxsso/api/v1/websso");

    String loginURL = ((TestSSOCookieFederationProvider) handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals(loginURL, "https://remotehost/notgateway/knoxsso/api/v1/websso?originalUrl=" + "https://remotehost/resource");
  }

  @Test
  public void testProxiedDefaultAuthenticationProviderURLWithoutMismatchInXForwardedHeader() throws Exception {
    // after KNOX-2467 enables jetty's xforwarded support this test has been
    // changed to expect the X-Forwarded Headers to be resolved by
    // httpRequest.getRequestURL instead of explicitly checking the headers
    // ourselves. Leaving the headers set to show this is a proxied request
    // but they really have no bearing on the results.

    // this is an odd test but we want to make sure that the removed code
    // that explicitly handled incoming xforwarded headers in the redirect
    // url is actuall removed and all handling of xforwarded is handled by
    // servlet container.
    Properties props = new Properties();
    props.setProperty("gateway.path", "notgateway");
    handler.init(new TestFilterConfig(props));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer("https://remotehost/resource")).anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PROTO)).andReturn("http").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_HOST)).andReturn("larryhost").anyTimes();
    EasyMock.expect(request.getHeader(SSOCookieFederationFilter.X_FORWARDED_PORT)).andReturn("7777").anyTimes();
    EasyMock.replay(request);

    String providerURL = ((TestSSOCookieFederationProvider) handler).deriveDefaultAuthenticationProviderUrl(request);
    Assert.assertNotNull("LoginURL should not be null.", providerURL);
    Assert.assertEquals(providerURL, "https://remotehost/notgateway/knoxsso/api/v1/websso");

    String loginURL = ((TestSSOCookieFederationProvider) handler).constructLoginURL(request);
    Assert.assertNotNull("LoginURL should not be null.", loginURL);
    Assert.assertEquals(loginURL, "https://remotehost/notgateway/knoxsso/api/v1/websso?originalUrl=" + "https://remotehost/resource");
  }

  @Override
  protected String getVerificationPemProperty() {
    return SSOCookieFederationFilter.SSO_VERIFICATION_PEM;
  }

  private static class TestSSOCookieFederationProvider extends SSOCookieFederationFilter {
    @Override
    public String constructLoginURL(HttpServletRequest req) {
      return super.constructLoginURL(req);
    }

    void setTokenService(JWTokenAuthority ts) {
      authority = ts;
    }
  }
}
