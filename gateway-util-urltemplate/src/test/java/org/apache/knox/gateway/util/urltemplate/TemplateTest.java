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
package org.apache.knox.gateway.util.urltemplate;

import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class TemplateTest {

  @Test
  public void testParseNonUrlKnox310() throws URISyntaxException {
    String input;
    String output;
    Template template;

    input = "{X}";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "{}";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    // {X}
    // *
    // **
    // {*}
    // {**}

    input = "*,${";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "$";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "$$";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "*,$";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "*.,${";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "{";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "}";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "${X}";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "{$X}";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

    input = "/var/lib/oozie/*.jar,/usr/lib/hadoop/client/*.jar,/usr/lib/oozie/libserver/*.jar,${catalina.home}/lib,${catalina.home}/lib/*.jar";
    template = Parser.parseTemplate( input );
    output = template.toString();
    assertThat( output, is( input ) );

  }

  @Test
  public void testToString() throws Exception {
    String text = "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment";
    Template template = Parser.parseTemplate( text );
    String actual = template.toString();
    assertThat( actual, equalTo( text ) );

    text = "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment";
    template = Parser.parseLiteral( text );
    actual = template.toString();
    assertThat( actual, equalTo( text ) );

    text = "{*}://{host}:{*}/{**}?{**}";
    template = Parser.parseTemplate( text );
    actual = template.toString();
    assertThat( template.getScheme().getParamName(), is( "*" ) );
    assertThat( template.getScheme().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getScheme().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getHost().getParamName(), is( "host" ) );
    assertThat( template.getHost().getFirstValue().getOriginalPattern(), nullValue() );
    assertThat( template.getHost().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPort().getParamName(), is( "*" ) );
    assertThat( template.getPort().getFirstValue().getOriginalPattern(), is( nullValue() ) );
    assertThat( template.getPort().getFirstValue().getEffectivePattern(), is( "*" ) );
    assertThat( template.getPath().size(), is( 1 ) );
    assertThat( template.getPath().get( 0 ).getParamName(), is( "**" ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getOriginalPattern(), is( nullValue() ) );
    assertThat( template.getPath().get( 0 ).getFirstValue().getEffectivePattern(), is( "**" ) );
    assertThat( template.getQuery().size(), is( 0 ) );
    assertThat( template.getExtra().getParamName(), is( "**" ) );
    assertThat( template.getExtra().getFirstValue().getOriginalPattern(), is( nullValue() ) );
    assertThat( template.getExtra().getFirstValue().getEffectivePattern(), is( "**" ) );
    //text = "{*=*}://{host=*}:{*=*}/{**=**}?{**=**}";
    assertThat( actual, is( text ) );

    text = "/path/**?**";
    template = Parser.parseTemplate( text );
    actual = template.toString();
    assertThat( actual, is( text ) );

    text = "host:42";
    template = Parser.parseTemplate( text );
    actual = template.toString();
    assertThat( actual, is( text ) );
  }

  @Test
  public void testHashCode() throws Exception {
    Template t1 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t2 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t3 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment2" );

    assertThat( t1.hashCode(), equalTo( t2.hashCode() ) );
    assertThat( t1.hashCode(), not( equalTo( t3.hashCode() ) ) );
  }

  @Test
  public void testEquals() throws Exception {
    Template t1 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t2 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment1" );
    Template t3 = Parser.parseTemplate( "scheme://username:password@host:port/top/mid/bot/file?query=value#fragment2" );

    assertThat( t1.equals( t2 ), equalTo( true ) );
    assertThat( t1.equals( t3 ), equalTo( false ) );
  }

}
