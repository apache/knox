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
package org.apache.knox.gateway.descriptor;

import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.fail;

public class GatewayDescriptorTest {

  @Test
  public void testGatewayDescriptorConstruction() {

    GatewayDescriptor descriptor = GatewayDescriptorFactory.create()
        .addParam().name( "cluster-param1-name" ).value( "cluster-param1-value" ).up()
        .addParam().name( "cluster-param2-name" ).value( "cluster-param2-value" ).up()
        .addResource()
          .pattern( "resource1-source" )
          //.target( "resource1-target" )
          //.param().name( "resource1-param1-name" ).value( "resource-param1-value" ).up()
          //.param().name( "resource1-param2-name" ).value( "resource-param2-value" ).up()
        .addFilter()
            .role( "resource1-filter1-role" )
            .impl( "resource1-filter1-impl" )
            .param()
              .name( "resource1-filter1-param1-name" )
              .value( "resource1-filter1-param1-value" ).up()
            .param()
              .name( "resource1-filter1-param2-name" )
              .value( "resource1-filter1-param2-value" ).up().up()
          .addFilter()
            .role( "resource1-filter2-role" )
            .impl( "resource1-filter2-impl" ).up().up()
        .addResource()
          .pattern( "resource2-source" )
          //.target( "resource2-target" )
          .addFilter()
            .param().up().up().up();

    assertThat( descriptor, notNullValue() );
    assertThat( descriptor.resources().size(), is( 2 ) );

    ResourceDescriptor resource1 = descriptor.resources().get( 0 );
    assertThat( resource1, notNullValue() );
    assertThat( resource1.pattern(), is( "resource1-source" ) );
    //assertThat( resource1.target(), is( "resource1-target" ) );

    assertThat( resource1.filters().size(), is( 2 ) );

    FilterDescriptor filter1 = resource1.filters().get( 0 );
    assertThat( filter1, notNullValue() );
    assertThat( filter1.role(), is( "resource1-filter1-role" ) );
    assertThat( filter1.impl(), is( "resource1-filter1-impl" ) );

    assertThat( filter1.params().size(), is( 2 ) );

    FilterParamDescriptor param1 = filter1.params().get( 0 );
    assertThat( param1, notNullValue() );
    assertThat( param1.name(), is( "resource1-filter1-param1-name" ) );
    assertThat( param1.value(), is( "resource1-filter1-param1-value" ) );

    FilterParamDescriptor param2 = filter1.params().get( 1 );
    assertThat( param2, notNullValue() );
    assertThat( param2.name(), is( "resource1-filter1-param2-name" ) );
    assertThat( param2.value(), is( "resource1-filter1-param2-value" ) );

    FilterDescriptor filter2 = resource1.filters().get( 1 );
    assertThat( filter2, notNullValue() );
    assertThat( filter2.role(), is( "resource1-filter2-role" ) );
    assertThat( filter2.impl(), is( "resource1-filter2-impl" ) );

    ResourceDescriptor resource2 = descriptor.resources().get( 1 );
    assertThat( resource2, notNullValue() );
    assertThat( resource2.pattern(), is( "resource2-source" ) );
    //assertThat( resource2.target(), is( "resource2-target" ) );
  }

  @Test
  public void testInvalidImportExportFormats() throws IOException {
    try {
      GatewayDescriptorFactory.load( "INVALID", null );
      fail( "Expected IllegalArgumentException" );
    } catch( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "INVALID" ) );
    }

    try {
      GatewayDescriptorFactory.store( GatewayDescriptorFactory.create(), "INVALID", null );
      fail( "Expected IllegalArgumentException" );
    } catch( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "INVALID" ) );
    }

  }

}
