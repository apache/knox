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
package org.apache.knox.gateway.aws.utils;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;

import java.util.Date;
import java.util.Optional;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.junit.Before;
import org.junit.Test;

public class CookieUtilsTest {

  private static final String TEST_COOKIE_NAME = "test-cookie";
  private static final String TEST_COOKIE_VALUE = "test-cookie-value";
  private static final String TEST_COOKIE_DOMAIN = "www.test.com";
  private static final String TEST_COOKIE_PATH = "/";
  private static final String NON_EXISTENT_COOKIE_NAME = "unknown-cookie";
  private static final int TEST_COOKIE_MAX_AGE = 12345678;

  private HttpServletRequest request = mock(HttpServletRequest.class);

  @Before
  public void setUp() {
    Cookie testCookie = new Cookie(TEST_COOKIE_NAME, TEST_COOKIE_VALUE);
    testCookie.setMaxAge(TEST_COOKIE_MAX_AGE);
    Cookie[] cookies = new Cookie[1] ;
    cookies[0] = testCookie;
    expect(request.getCookies()).andStubReturn(cookies);
    replay(request);
  }

  @Test
  public void getExistentCookieValue() {
        assertThat(CookieUtils.getCookieValue(request, TEST_COOKIE_NAME).get(),
        is(TEST_COOKIE_VALUE));
  }

  @Test
  public void getNonExistentCookieValue() {
    assertThat(CookieUtils.getCookieValue(request, NON_EXISTENT_COOKIE_NAME), is(Optional.empty()));
  }

  @Test
  public void createCookie() {
    Cookie cookie = CookieUtils.createCookie(TEST_COOKIE_NAME, TEST_COOKIE_VALUE, TEST_COOKIE_DOMAIN,
        TEST_COOKIE_PATH, TEST_COOKIE_MAX_AGE, true, true);
    assertThat(cookie.getName(), is(TEST_COOKIE_NAME));
    assertThat(cookie.getValue(), is(TEST_COOKIE_VALUE));
    assertThat(cookie.getDomain(), is(TEST_COOKIE_DOMAIN));
    assertThat(cookie.getPath(), is(TEST_COOKIE_PATH));
    assertThat(cookie.getMaxAge(), is(TEST_COOKIE_MAX_AGE));
    assertThat(cookie.isHttpOnly(), is(true));
    assertThat(cookie.getSecure(), is(true));
  }

  @Test
  public void getCookieAgeMatchingAwsCredentials() {
    AwsSamlCredentials awsSamlCredentials = AwsSamlCredentials.builder()
        .expiration(new Date().getTime() + TEST_COOKIE_MAX_AGE).build();
    assertThat((double)CookieUtils.getCookieAgeMatchingAwsCredentials(awsSamlCredentials),
        closeTo(TEST_COOKIE_MAX_AGE/1000, 10));
  }
}
