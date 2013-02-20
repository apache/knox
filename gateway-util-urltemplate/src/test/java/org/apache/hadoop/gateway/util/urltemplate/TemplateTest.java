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
package org.apache.hadoop.gateway.util.urltemplate;

import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class TemplateTest {

  @Test
  public void testToString() throws Exception {
    String expect = "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment";
    Template template = Parser.parse( expect );
    String actual = template.toString();
    assertThat( actual, equalTo( expect ) );

    template = Parser.parse( "{*}://{host}:{*}/{**}?{**}" );
    actual = template.toString();
    expect = "{*=*}://{host=*}:{*=*}/{**=**}?{**=**}";
    assertThat( actual, is( expect ) );

    template = Parser.parse( "/path/**?**" );
    actual = template.toString();
    expect = "/path/**?**";
    assertThat( actual, is( expect ) );
  }

  @Test
  public void testHashCode() throws Exception {
    Template t1 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t2 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t3 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment2" );

    assertThat( t1.hashCode(), equalTo( t2.hashCode() ) );
    assertThat( t1.hashCode(), not( equalTo( t3.hashCode() ) ) );
  }

  @Test
  public void testEquals() throws Exception {
    Template t1 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t2 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t3 = Parser.parse( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment2" );

    assertThat( t1.equals( t2 ), equalTo( true ) );
    assertThat( t1.equals( t3 ), equalTo( false ) );
  }

}
