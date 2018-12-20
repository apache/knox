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
package org.apache.knox.gateway.i18n.resources;

import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Locale;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class ResourcesTest {
  private Locale locale;

  @Before
  public void setUp() {
    locale = Locale.getDefault();
  }

  @After
  public void tearDown() {
    Locale.setDefault( locale );
  }

  @Test
  public void testResourceFormatting() {
    ResourcesFormattingSubject res = ResourcesFactory.get( ResourcesFormattingSubject.class );

    assertThat(
        res.withoutAnnotationsOrParameters(),
        equalTo( "withoutAnnotationsOrParameters" ) );

    assertThat(
        res.withAnnotationWithoutPatternOneParam( 42 ),
        equalTo( "withAnnotationWithoutPatternOneParam(\"42\")" ) );

    assertThat(
        res.withAnnotationWithPatternOneParam( 72 ),
        equalTo( "before72after" ) );

    assertThat(
        res.withAnnotationWithSimplePatternOneParam( 33 ),
        equalTo( "33" ) );

    assertThat(
        res.withoutAnnotationsWithElevenParams( "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10", "v11" ),
        equalTo( "withoutAnnotationsWithElevenParams(\"v1\",\"v2\",\"v3\",\"v4\",\"v5\",\"v6\",\"v7\",\"v8\",\"v9\",\"v10\",\"v11\")" ) );

    assertThat(
        res.withoutAnnotationsWithOneParam( 17 ),
        equalTo( "withoutAnnotationsWithOneParam(\"17\")" ) );

    assertThat(
        res.withMoreFormatParamsThanMethodParams( 7 ),
        equalTo( "7,{1}" ) );

    assertThat(
        res.withLessFormatParamsThanMethodParams( 7, 11 ),
        equalTo( "7" ) );
  }

  @Test
  public void testResourceLocales() {
    ResourcesLocaleSubject res = ResourcesFactory.get( ResourcesLocaleSubject.class );

    Locale.setDefault( Locale.CHINESE ); // Change to something that we won't have test bundles for.
    assertThat( res.testResource( "queryParam" ), equalTo( "default=[queryParam]" ) );

    Locale.setDefault( Locale.ENGLISH );
    assertThat( res.testResource( "queryParam" ), equalTo( "en=[queryParam]" ) );

    Locale.setDefault( Locale.US );
    assertThat( res.testResource( "queryParam" ), equalTo( "us=[queryParam]" ) );

    Locale.setDefault( Locale.UK );
    assertThat( res.testResource( "queryParam" ), equalTo( "uk=[queryParam]" ) );
  }

  @Test
  public void testNamedBundle() {
    ResourcesNamedSubject res = ResourcesFactory.get( ResourcesNamedSubject.class );

    Locale.setDefault( Locale.CHINESE ); // Change to something that we won't have test bundles for.
    assertThat( res.testResource( "queryParam" ), equalTo( "default=[queryParam]" ) );

    Locale.setDefault( Locale.CANADA );
    assertThat( res.testResource( "queryParam" ), equalTo( "ca=[queryParam]" ) );
  }
}
