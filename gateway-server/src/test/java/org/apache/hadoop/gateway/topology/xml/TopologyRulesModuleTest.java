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
package org.apache.hadoop.gateway.topology.xml;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.hadoop.gateway.topology.Provider;
import org.apache.hadoop.gateway.topology.Service;
import org.apache.hadoop.gateway.topology.Topology;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

public class TopologyRulesModuleTest {

  private static DigesterLoader loader;

  @Before
  public void setUp() throws Exception {
    loader = newLoader( new XmlTopologyRules() );
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testParseSimpleTopologyXml() throws IOException, SAXException {
    Digester digester = loader.newDigester();
    String name = "org/apache/hadoop/gateway/topology/xml/simple-topology.xml";
    URL url = ClassLoader.getSystemResource( name );
    assertThat( "Failed to find URL for resource " + name, url, notNullValue() );
    File file = new File( url.getFile() );
    Topology topology = digester.parse( url );
    assertThat( "Failed to parse resource " + name, topology, notNullValue() );
    topology.setTimestamp( file.lastModified() );

    assertThat( topology.getName(), is( "topology" ) );
    assertThat( topology.getTimestamp(), is( file.lastModified() ) );
    assertThat( topology.getServices().size(), is( 1 ) );

    Service comp = topology.getServices().iterator().next();
    assertThat( comp, notNullValue() );
    assertThat( comp.getRole(), is( "NAMENODE" ) );
    assertThat( comp.getUrl(), is( new URL( "http://host:80/webhdfs/v1" ) ) );

    Provider provider = topology.getProviders().iterator().next();
    assertThat( provider, notNullValue() );
    assertThat( provider.isEnabled(), is(true) );
    assertThat( provider.getRole(), is( "authentication" ) );
    assertThat( provider.getParams().size(), is(1));
  }

}
