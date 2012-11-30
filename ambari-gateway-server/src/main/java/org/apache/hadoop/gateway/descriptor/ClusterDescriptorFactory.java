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
package org.apache.hadoop.gateway.descriptor;

import org.apache.hadoop.gateway.deploy.ClusterFilterDescriptorFactory;
import org.apache.hadoop.gateway.deploy.ClusterResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.impl.ClusterDescriptorImpl;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorImporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorImporter;
import org.apache.hadoop.gateway.topology.ClusterTopologyComponent;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ClusterDescriptorFactory {

  private static Map<String, ClusterResourceDescriptorFactory> RESOURCE_FACTORIES = loadResourceFactories();
  private static Map<String, ClusterFilterDescriptorFactory> FILTER_FACTORIES = loadFilterFactories();
  private static Map<String, ClusterDescriptorImporter> IMPORTERS = loadImporters();
  private static Map<String, ClusterDescriptorExporter> EXPORTERS = loadExporters();

  public static ClusterDescriptor create() {
    return new ClusterDescriptorImpl();
  }

  public static ClusterDescriptor load( String format, Reader reader ) throws IOException {
    ClusterDescriptorImporter importer = IMPORTERS.get( format );
    if( importer == null ) {
      throw new IllegalArgumentException( "No importer for format " + format );
    }
    return importer.load( reader );
  }

  public static void store( ClusterDescriptor descriptor, String format, Writer writer ) throws IOException {
    ClusterDescriptorExporter exporter = EXPORTERS.get( format );
    if( exporter == null ) {
      throw new IllegalArgumentException( "No exporter for format " + format );
    }
    exporter.store( descriptor, writer );
  }

  public static ClusterResourceDescriptorFactory getClusterResourceDescriptorFactory( ClusterTopologyComponent component ) {
    return RESOURCE_FACTORIES.get( component.getRole() );
  }

  public static ClusterFilterDescriptorFactory getClusterFilterDescriptorFactory( String filterRole ) {
    return FILTER_FACTORIES.get( filterRole );
  }

  private static Map<String, ClusterDescriptorImporter> loadImporters() {
    Map<String, ClusterDescriptorImporter> map = new ConcurrentHashMap<String, ClusterDescriptorImporter>();
    map.put( "xml", new XmlClusterDescriptorImporter() );
    return map;
  }

  private static Map<String, ClusterDescriptorExporter> loadExporters() {
    Map<String, ClusterDescriptorExporter> map = new ConcurrentHashMap<String, ClusterDescriptorExporter>();
    map.put( "xml", new XmlClusterDescriptorExporter() );
    return map;
  }

  private static Map<String, ClusterResourceDescriptorFactory> loadResourceFactories() {
    Map<String, ClusterResourceDescriptorFactory> map = new HashMap<String, ClusterResourceDescriptorFactory>();
    ServiceLoader<ClusterResourceDescriptorFactory> loader = ServiceLoader.load( ClusterResourceDescriptorFactory.class );
    Iterator<ClusterResourceDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      ClusterResourceDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedResourceRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }

  private static Map<String, ClusterFilterDescriptorFactory> loadFilterFactories() {
    Map<String, ClusterFilterDescriptorFactory> map = new HashMap<String, ClusterFilterDescriptorFactory>();
    ServiceLoader<ClusterFilterDescriptorFactory> loader = ServiceLoader.load( ClusterFilterDescriptorFactory.class );
    Iterator<ClusterFilterDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      ClusterFilterDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedFilterRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }

}
