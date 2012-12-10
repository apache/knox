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

import org.apache.hadoop.gateway.deploy.FilterDescriptorFactory;
import org.apache.hadoop.gateway.deploy.ResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.impl.GatewayDescriptorImpl;
import org.apache.hadoop.gateway.descriptor.xml.XmlGatewayDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlGatewayDescriptorImporter;
import org.apache.hadoop.gateway.topology.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class GatewayDescriptorFactory {

  private static Map<String, ResourceDescriptorFactory> RESOURCE_FACTORIES = loadResourceFactories();
  private static Map<String, FilterDescriptorFactory> FILTER_FACTORIES = loadFilterFactories();
  private static Map<String, GatewayDescriptorImporter> IMPORTERS = loadImporters();
  private static Map<String, GatewayDescriptorExporter> EXPORTERS = loadExporters();
  private static Properties ROLE_TO_FILTER_MAPPING = loadRoleToFilterClassMapping();
  
  public static GatewayDescriptor create() {
    return new GatewayDescriptorImpl();
  }

  public static GatewayDescriptor load( String format, Reader reader ) throws IOException {
    GatewayDescriptorImporter importer = IMPORTERS.get( format );
    if( importer == null ) {
      throw new IllegalArgumentException( "No importer for descriptor format " + format );
    }
    return importer.load( reader );
  }

  public static void store( GatewayDescriptor descriptor, String format, Writer writer ) throws IOException {
    GatewayDescriptorExporter exporter = EXPORTERS.get( format );
    if( exporter == null ) {
      throw new IllegalArgumentException( "No exporter for descriptor format " + format );
    }
    exporter.store( descriptor, writer );
  }

  public static ResourceDescriptorFactory getClusterResourceDescriptorFactory( Service service ) {
    return RESOURCE_FACTORIES.get( service.getRole() );
  }

  public static FilterDescriptorFactory getClusterFilterDescriptorFactory( String filterRole ) {
    return FILTER_FACTORIES.get( filterRole );
  }
  
  public static String getClusterProviderFilterClassName(String role, String defaultClassName) {
    return ROLE_TO_FILTER_MAPPING.getProperty(role, defaultClassName);
  }

  private static Map<String, GatewayDescriptorImporter> loadImporters() {
    Map<String, GatewayDescriptorImporter> map = new ConcurrentHashMap<String, GatewayDescriptorImporter>();
    map.put( "xml", new XmlGatewayDescriptorImporter() );
    return map;
  }

  private static Map<String, GatewayDescriptorExporter> loadExporters() {
    Map<String, GatewayDescriptorExporter> map = new ConcurrentHashMap<String, GatewayDescriptorExporter>();
    map.put( "xml", new XmlGatewayDescriptorExporter() );
    return map;
  }

  private static Map<String, ResourceDescriptorFactory> loadResourceFactories() {
    Map<String, ResourceDescriptorFactory> map = new HashMap<String, ResourceDescriptorFactory>();
    ServiceLoader<ResourceDescriptorFactory> loader = ServiceLoader.load( ResourceDescriptorFactory.class );
    Iterator<ResourceDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      ResourceDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedResourceRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }

  private static Map<String, FilterDescriptorFactory> loadFilterFactories() {
    Map<String, FilterDescriptorFactory> map = new HashMap<String, FilterDescriptorFactory>();
    ServiceLoader<FilterDescriptorFactory> loader = ServiceLoader.load( FilterDescriptorFactory.class );
    Iterator<FilterDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      FilterDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedFilterRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }
  
  private static Properties loadRoleToFilterClassMapping() {
    InputStream inputStream = GatewayDescriptorFactory.class.getClassLoader().getResourceAsStream("META-INF/filter-provider.properties");
    Properties properties = new Properties();  
    
    if ( inputStream != null ) {  
      try {  
        properties.load(inputStream);  
      }  
      catch ( IOException ioe ) {  
        ioe.printStackTrace();  
      }
    }
    return properties;
  }

}
