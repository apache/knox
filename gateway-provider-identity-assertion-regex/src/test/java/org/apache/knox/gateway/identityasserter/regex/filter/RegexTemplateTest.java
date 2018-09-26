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
package org.apache.knox.gateway.identityasserter.regex.filter;

import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RegexTemplateTest {

  @Test
  public void testExtractUsernameFromEmailAddress() {

    RegexTemplate template;
    String actual;

    template = new RegexTemplate( "(.*)@.*", "prefix_{1}_suffix" );
    actual = template.apply( "member@apache.org" );
    assertThat( actual, is( "prefix_member_suffix" ) );

    template = new RegexTemplate( "(.*)@.*", "prefix_{0}_suffix" );
    actual = template.apply( "member@apache.org" );
    assertThat( actual, is( "prefix_member@apache.org_suffix" ) );

    template = new RegexTemplate( "(.*)@.*", "prefix_{1}_{a}_suffix" );
    actual = template.apply( "member@apache.org" );
    assertThat( actual, is( "prefix_member_{a}_suffix" ) );

  }

  @Test
  public void testExtractUsernameFromEmailAddressAndMapDomain() {

    RegexTemplate template;
    Map<String,String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    map.put( "us", "USA" );
    map.put( "ca", "CANADA" );

    String actual;

    template = new RegexTemplate( "(.*)@(.*?)\\..*", "prefix_{1}:{[2]}_suffix", map, false );
    actual = template.apply( "member@us.apache.org" );
    assertThat( actual, is( "prefix_member:USA_suffix" ) );

    actual = template.apply( "member@ca.apache.org" );
    assertThat( actual, is( "prefix_member:CANADA_suffix" ) );

    actual = template.apply( "member@nj.apache.org" );
    assertThat( actual, is( "prefix_member:_suffix" ) );

  }

  @Test
  public void testLookupFailure() {

    RegexTemplate template;
    Map<String,String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    map.put( "us", "USA" );
    map.put( "ca", "CANADA" );

    String actual;

    template = new RegexTemplate( "(.*)@(.*?)\\..*", "prefix_{1}:{[2]}_suffix", map, true );
    actual = template.apply( "member@us.apache.org" );
    assertThat( actual, is( "prefix_member:USA_suffix" ) );

    actual = template.apply( "member@ca.apache.org" );
    assertThat( actual, is( "prefix_member:CANADA_suffix" ) );

    actual = template.apply( "member@nj.apache.org" );
    assertThat( actual, is( "prefix_member:nj_suffix" ) );

  }
}
