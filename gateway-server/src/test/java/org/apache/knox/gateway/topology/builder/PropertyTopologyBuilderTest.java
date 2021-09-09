/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.builder;

import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.builder.property.Property;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

public class PropertyTopologyBuilderTest {
  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "miss_prop", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test
  public void testBuildSuccessfulForTopologyProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.name", "topology" ) );
    Topology topology = propertyTopologyBuilder.build();

    assertThat( topology, notNullValue() );
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongTopologyProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.miss_prop", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongGatewayToken() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.miss_prop", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProviderToken1() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProviderToken2() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProviderToken3() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test
  public void testBuildSuccessfulForProviderProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider.enabled", "value" ) );
    Topology topology = propertyTopologyBuilder.build();

    assertThat( topology, notNullValue() );
    assertThat( topology.getProviders().size(), is( 1 ) );
    assertThat( topology.getProviders().iterator().next().isEnabled(), is( false ) );
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProviderProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider.miss_prop", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongProviderParamToken1() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider.param", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForEmptyProviderParamName() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider.param.", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForEmptyProviderParamValue() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.provider.authentication.ShiroProvider.param.name1", "" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongServiceToken1() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.service", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongServiceToken2() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.service.WEBHDFS", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongServiceToken3() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.service.WEBHDFS.", "value" ) );
    propertyTopologyBuilder.build();
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildSuccessfulForServiceProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.service.WEBHDFS..url", "http://host:50070/webhdfs" ) );
    Topology topology = propertyTopologyBuilder.build();

    assertThat( topology, notNullValue() );
  }

  @Test( expected = IllegalArgumentException.class )
  public void testBuildFailedForWrongServiceProperty() {
    PropertyTopologyBuilder propertyTopologyBuilder = new PropertyTopologyBuilder();
    propertyTopologyBuilder.addProperty( new Property( "topology.gateway.service.WEBHDFS..miss_prop", "value" ) );
    propertyTopologyBuilder.build();
  }
}
