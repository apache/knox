/**
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

import java.util.List;

import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.*;

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

    store = new HadoopAuthCookieStore();
    store.addCookie( new BasicClientCookie( "hadoop.auth", rawValue ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is(quotedValue) );

    store = new HadoopAuthCookieStore();
    store.addCookie( new BasicClientCookie( "hadoop.auth", quotedValue ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is(quotedValue) );

    store = new HadoopAuthCookieStore();
    store.addCookie( new BasicClientCookie( "hadoop.auth", null ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is(nullValue()) );

    store = new HadoopAuthCookieStore();
    store.addCookie( new BasicClientCookie( "hadoop.auth", "" ) );
    cookies = store.getCookies();
    cookie = cookies.get( 0 );
    assertThat( cookie.getValue(), is("") );
  }

}