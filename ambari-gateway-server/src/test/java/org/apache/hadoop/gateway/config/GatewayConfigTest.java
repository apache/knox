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
package org.apache.hadoop.gateway.config;

import org.apache.hadoop.test.FastTests;
import org.apache.hadoop.test.UnitTests;
import org.apache.commons.digester3.AbstractObjectCreationFactory;
import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ExtendedBaseRules;
import org.apache.commons.digester3.Rule;
import org.apache.hadoop.test.FastTests;
import org.apache.hadoop.test.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.Properties;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class } )
public class GatewayConfigTest {

  public class MapRule extends Rule {
    @Override
    public void body( String namespace, String name, String text ) throws Exception {
      super.body( namespace, name, text );
      Properties props = this.getDigester().peek();
      props.put( name, text );
    }
  }

  public static class PropertiesCreator extends AbstractObjectCreationFactory<Properties> {
    @Override
    public Properties createObject( Attributes attributes ) throws Exception {
      return new Properties();
    }
  }

  @Test
  public void testMap() throws IOException, SAXException {
    String xml = "<map><name>value</name></map>";

    Digester digester = new Digester();
    digester.setRules( new ExtendedBaseRules() );
    digester.setValidating( false );
    digester.addObjectCreate( "map", Properties.class );
    digester.addRule( "map/*", new MapRule() );

    Properties props = digester.parse( new StringReader( xml ) );

    assertThat( props, notNullValue() );
    assertThat( (String)props.get( "name" ), equalTo( "value" ) );
  }

  @Test
  public void testFactory() throws IOException, SAXException {
    String xml = "<map><name>value</name></map>";

    Digester digester = new Digester();
    digester.setRules( new ExtendedBaseRules() );
    digester.setValidating( false );
    digester.addFactoryCreate( "map", PropertiesCreator.class );
    digester.addRule( "map/*", new MapRule() );

    Properties props = digester.parse( new StringReader( xml ) );

    assertThat( props, notNullValue() );
    assertThat( (String)props.get( "name" ), equalTo( "value" ) );
  }

  @Test
  public void testConfig() throws IOException, SAXException {
    URL configUrl = ClassLoader.getSystemResource( "gateway-digest-test.xml" );

    Config gatewayConfig = GatewayConfigFactory.create( configUrl, null );
    assertThat( gatewayConfig.get( "name" ), equalTo( "org.apache.org.apache.hadoop.gateway-name" ) );

    assertThat( gatewayConfig.getChildren().size(), equalTo( 2 ) );

    Config serviceConfig1 = gatewayConfig.getChildren().get( "service1-name" );
    assertThat( serviceConfig1, notNullValue() );
    assertThat( serviceConfig1.get( "name" ), equalTo( "service1-name" ) );
    assertThat( serviceConfig1.get( "source" ), equalTo( "service1-source" ) );
    assertThat( serviceConfig1.get( "target" ), equalTo( "service1-target" ) );

    assertThat( serviceConfig1.getChildren().size(), equalTo( 2 ));

    Config filterConfig1 = serviceConfig1.getChildren().get( "filter1-name" );
    assertThat( filterConfig1, notNullValue() );
    assertThat( filterConfig1.get( "name" ), equalTo( "filter1-name" ) );
    assertThat( filterConfig1.get( "source" ), equalTo( "service1-source" ) );

    Config filterConfig2 = serviceConfig1.getChildren().get( "filter2-name" );
    assertThat( filterConfig2, notNullValue() );
    assertThat( filterConfig2.get( "name" ), equalTo( "filter2-name" ) );
    assertThat( filterConfig2.get( "target"), equalTo( "service1-target" ) );

    Config serviceConfig2 = gatewayConfig.getChildren().get( "service2-name" );
    assertThat( serviceConfig2, notNullValue() );
    assertThat( serviceConfig2.get( "name" ), equalTo( "service2-name" ) );
    assertThat( serviceConfig2.get( "source" ), equalTo( "service2-source" ) );
    assertThat( serviceConfig2.get( "target" ), equalTo( "service2-target" ) );

  }

}
