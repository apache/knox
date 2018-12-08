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
package org.apache.knox.gateway.launcher;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class ConfigTest {

  private static final String NEWLINE = System.getProperty( "line.separator" );

  @Test
  public void testLoad() throws Exception {
    Config c;
    String s;

    c = new Config();
    c.load( null );

    c = new Config();
    s = "";
    c.load( new StringReader( s ) );
    assertThat( c.get( null, null ), nullValue() );

    c = new Config();
    s = "name=value";
    c.load( new StringReader( s ) );
    assertThat( c.get( null, "name" ), is( "value" ) );
    assertThat( c.get( null, "wrong-name" ), nullValue() );

    s = "name1=value1" + NEWLINE + "[section1]" + NEWLINE + "name1=value2";
    c = new Config();
    c.load( new StringReader( s ) );
    assertThat( c.get( "", "name1" ), is( "value1" ) );
    assertThat( c.get( "", "name2" ), nullValue() );
    assertThat( c.get( "section1", "name1" ), is( "value2" ) );
    assertThat( c.get( "section1", "name2" ), nullValue() );
  }

  @Test
  public void testSave() throws Exception {
    Config c;
    StringWriter w;

    c = new Config();
    c.save( null );

    c = new Config();
    w = new StringWriter();
    c.save( w );
    assertThat( w.toString(), is( "" ) );

    c = new Config();
    c.set( null, null, null );
    w = new StringWriter();
    c.save( w );
    assertThat( w.toString(), is( "=" + NEWLINE ) );

    c = new Config();
    c.set( null, "name1", "value1" );
    c.set( "section1", "name1", "value2" );
    w = new StringWriter();
    c.save( w );
    assertThat( w.toString(), is( "name1=value1" + NEWLINE + NEWLINE + "[section1]" + NEWLINE + "name1=value2" ) );
  }

  @Test
  public void testSetGet() throws Exception {
    Config c;

    c = new Config();

    c.set( null, null, "value" );
    assertThat( c.get( null, null ), is( "value" ) );
    assertThat( c.get( "", null ), is( "value" ) );
    assertThat( c.get( null, "" ), is( "value" ) );
    assertThat( c.get( "", "" ), is( "value" ) );

    c = new Config();
    assertThat( c.get( null, "name" ), nullValue() );
    c.set( null, "name", null );
    assertThat( c.get( null, "name" ), nullValue() );
    c.set( null, "name", "value1" );
    assertThat( c.get( null, "name" ), is( "value1" ) );
    assertThat( c.get( "section1", "name" ), is( "value1" ) );
    c.set( "section1", "name", null );
    assertThat( c.get( null, "name" ), is( "value1" ) );
    assertThat( c.get( "section1", "name" ), nullValue() );
    c.set( "section1", "name", "value2" );
    assertThat( c.get( null, "name" ), is( "value1" ) );
    assertThat( c.get( "section1", "name" ), is( "value2" ) );
    assertThat( c.get( "section2", "name" ), is( "value1" ) );
  }

}
