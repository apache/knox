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
package org.apache.knox.gateway.filter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class CompositeEnumerationTest {

  @Test
  public void testBasics() {

    String[] a = new String[]{ "1", "2" };
    Enumeration<String> ea = Collections.enumeration( Arrays.asList( a ) );

    String[] b = new String[]{ "3", "4" };
    Enumeration<String> eb = Collections.enumeration( Arrays.asList( b ) );

    CompositeEnumeration<String> ce = new CompositeEnumeration<>( ea, eb );

    assertThat( ce.nextElement(), is( "1" ) );
    assertThat( ce.nextElement(), is( "2" ) );
    assertThat( ce.nextElement(), is( "3" ) );
    assertThat( ce.nextElement(), is( "4" ) );
    assertThat( ce.hasMoreElements(), is( false ) );

  }

  @Test
  public void testSingleValues() {
    String[] a = new String[]{ "1" };
    Enumeration<String> ea = Collections.enumeration( Arrays.asList( a ) );

    String[] b = new String[]{ "2" };
    Enumeration<String> eb = Collections.enumeration( Arrays.asList( b ) );

    CompositeEnumeration<String> ce = new CompositeEnumeration<>( ea, eb );

    assertThat( ce.nextElement(), is( "1" ) );
    assertThat( ce.nextElement(), is( "2" ) );
    assertThat( ce.hasMoreElements(), is( false ) );
  }

  @Test
  public void testEmptyEnumerations() {

    String[] a = new String[]{ "1", "2" };
    String[] b = new String[]{ "3", "4" };
    String[] c = new String[]{};

    Enumeration<String> e1 = Collections.enumeration( Arrays.asList( a ) );
    Enumeration<String> e2 = Collections.enumeration( Arrays.asList( c ) );
    CompositeEnumeration<String> ce = new CompositeEnumeration<>( e1, e2 );
    assertThat( ce.nextElement(), is( "1" ) );
    assertThat( ce.nextElement(), is( "2" ) );
    assertThat( ce.hasMoreElements(), is( false ) );

    e1 = Collections.enumeration( Arrays.asList( c ) );
    e2 = Collections.enumeration( Arrays.asList( a ) );
    ce = new CompositeEnumeration<>( e1, e2 );
    assertThat( ce.nextElement(), is( "1" ) );
    assertThat( ce.nextElement(), is( "2" ) );
    assertThat( ce.hasMoreElements(), is( false ) );

    e1 = Collections.enumeration( Arrays.asList( c ) );
    e2 = Collections.enumeration( Arrays.asList( c ) );
    ce = new CompositeEnumeration<>( e1, e2 );
    assertThat( ce.hasMoreElements(), is( false ) );
  }

  @Test
  public void testEmpty() {
    CompositeEnumeration<String> ce = new CompositeEnumeration<>();
    assertThat( ce.hasMoreElements(), is( false ) );

    try {
      ce.nextElement();
      fail( "Should have throws NoSuchElementExcpetion" );
    } catch( NoSuchElementException e ) {
      // Expected.
    }
  }

  @Test
  public void testNulls() {
    try {
      CompositeEnumeration<String> ce = new CompositeEnumeration<>( null );
      fail( "Expected IllegalArgumentException" );
    } catch( IllegalArgumentException e ) {
      // Expected.
    }
  }

}
