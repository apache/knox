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
package org.apache.knox.gateway.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class UrlsTest {

  /*
   * Domain name creation follows the following algorithm:
   * 1. if the incoming request hostname endsWith a configured domain suffix return the suffix - with prefixed dot
   * 2. if the request hostname is an ip address return null for default domain
   * 3. if the request hostname has less than 3 dots return null for default domain
   * 4. if request hostname has more than two dots strip the first element and return the remainder as domain
   */
  @Test
  public void testDomainNameCreation() throws Exception {
    // determine parent domain and wildcard the cookie domain with a dot prefix
    assertEquals(".local.com", Urls.getDomainName("http://www.local.com", null));
    assertEquals(".local.com", Urls.getDomainName("http://ljm.local.com", null));

    // test scenarios that will leverage the default cookie domain
    Assert.assertNull(Urls.getDomainName("http://local.home", null));
    Assert.assertNull(Urls.getDomainName("http://localhost", null)); // chrome may not allow this

    assertEquals(".home.test.com", Urls.getDomainName("http://local.home.test.com", null));

    // check the suffix config feature
    assertEquals(".test.com", Urls.getDomainName("http://local.home.test.com", ".test.com"));
    Assert.assertEquals(".novalocal", Urls.getDomainName("http://34526yewt.novalocal", ".novalocal"));

    // make sure that even if the suffix doesn't start with a dot that the domain does
    // if we are setting a domain suffix then we want a specific domain for SSO and that
    // will require all hosts in the domain in order for it to work
    Assert.assertEquals(".novalocal", Urls.getDomainName("http://34526yewt.novalocal", "novalocal"));

    // ip addresses can not be wildcarded - may be a completely different domain
    Assert.assertNull(Urls.getDomainName("http://127.0.0.1", null));

    /* Make sure we handle encoded characters properly here */
    assertEquals(".local.com", Urls.getDomainName("https://www.local.com:8443/gateway/manager/admin-ui?limit=25&query=hive_table+where+name%3D%22table_1%22", null));
    /* Make sure we handle un-encoded characters safely */
    assertEquals(".local.com", Urls.getDomainName("https://www.local.com:8443/gateway/manager/admin-ui/?limit=25&query=\"table_1\"", null));

  }

  @Test
  public void testTrimLeadingAndTrailingSlash() {
    assertEquals( "", Urls.trimLeadingAndTrailingSlash( null ) );
    assertEquals( "", Urls.trimLeadingAndTrailingSlash( "" ) );
    assertEquals( "", Urls.trimLeadingAndTrailingSlash( "/" ) );
    assertEquals( "", Urls.trimLeadingAndTrailingSlash( "//" ) );
    assertEquals( "x", Urls.trimLeadingAndTrailingSlash( "x" ) );
    assertEquals( "x/x", Urls.trimLeadingAndTrailingSlash( "x/x" ) );
    assertEquals( "x", Urls.trimLeadingAndTrailingSlash( "/x/" ) );
    assertEquals( "x/x", Urls.trimLeadingAndTrailingSlash( "x/x" ) );
  }

  @Test
  public void testTrimLeadingAndTrailingSlashJoin() throws Exception {
    assertEquals( "", Urls.trimLeadingAndTrailingSlashJoin( null ) );
    assertEquals( "", Urls.trimLeadingAndTrailingSlashJoin( "" ) );
    assertEquals( "", Urls.trimLeadingAndTrailingSlashJoin( "", "" ) );
    assertEquals( "x", Urls.trimLeadingAndTrailingSlashJoin( "x" ) );
    assertEquals( "x", Urls.trimLeadingAndTrailingSlashJoin( "x", "" ) );
    assertEquals( "x", Urls.trimLeadingAndTrailingSlashJoin( "", "x", "" ) );
    assertEquals( "x/x", Urls.trimLeadingAndTrailingSlashJoin( "", "x", "", "", "x" ) );
    assertEquals( "x/x", Urls.trimLeadingAndTrailingSlashJoin( null, "x", null, null, "x" ) );
    assertEquals( "x/y/z", Urls.trimLeadingAndTrailingSlashJoin( "x", "y", "z" ) );
  }

  @Test
  public void testURLEncoding() throws Exception {
    assertEquals( "%26", Urls.encode( "&" ) );
    assertEquals( "&query=hive_table", Urls.decode( "%26query=hive_table" ) );
    assertEquals( "%3F", Urls.encode( "?" ) );
  }

  @Test
  public void testContainsUserInfo() throws Exception {
    assertTrue(Urls.containsUserInfo( "https://www.local.com:8443aa@google.com"));
    assertFalse(Urls.containsUserInfo( "https://www.local.com:8443/google.com"));
  }
}
