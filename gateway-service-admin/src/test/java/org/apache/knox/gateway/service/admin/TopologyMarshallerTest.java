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
package org.apache.knox.gateway.service.admin;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.apache.knox.gateway.topology.Application;
import org.apache.knox.gateway.topology.Topology;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.xmlmatchers.transform.XmlConverters.the;
import static org.xmlmatchers.xpath.HasXPath.hasXPath;
import static org.xmlmatchers.xpath.XpathReturnType.returningAString;

public class TopologyMarshallerTest {

  @Test
  public void testTopologyMarshalling() throws Exception {
    Topology topology = new Topology();
    Application app = new Application();
    app.setName( "test-app-name" );
    topology.addApplication( app );

    StringWriter writer = new StringWriter();
    String xml;

    Map<String,Object> properties = new HashMap<>(2);
    properties.put( "eclipselink-oxm-xml",
        "org/apache/knox/gateway/topology/topology_binding-xml.xml");
    properties.put( "eclipselink.media-type", "application/xml" );
    JAXBContext jaxbContext = JAXBContext.newInstance( Topology.class.getPackage().getName(), Topology.class.getClassLoader() , properties );
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty( Marshaller.JAXB_FORMATTED_OUTPUT, true );
    marshaller.marshal( topology, writer );
    writer.close();
    xml = writer.toString();
    assertThat( the( xml ), hasXPath( "/topology/application/name", returningAString(), is("test-app-name") ) );
  }

}