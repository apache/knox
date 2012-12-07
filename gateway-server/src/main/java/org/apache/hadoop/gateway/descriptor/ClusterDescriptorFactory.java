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

import org.apache.hadoop.gateway.deploy.DeploymentFilterDescriptorFactory;
import org.apache.hadoop.gateway.deploy.DeploymentResourceDescriptorFactory;
import org.apache.hadoop.gateway.descriptor.impl.ClusterDescriptorImpl;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorImporter;
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

public abstract class ClusterDescriptorFactory {

  private static Map<String, DeploymentResourceDescriptorFactory> RESOURCE_FACTORIES = loadResourceFactories();
  private static Map<String, DeploymentFilterDescriptorFactory> FILTER_FACTORIES = loadFilterFactories();
  private static Map<String, ClusterDescriptorImporter> IMPORTERS = loadImporters();
  private static Map<String, ClusterDescriptorExporter> EXPORTERS = loadExporters();
  private static Properties ROLE_TO_FILTER_MAPPING = loadRoleToFilterClassMapping();
  
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

  public static DeploymentResourceDescriptorFactory getClusterResourceDescriptorFactory( Service service ) {
    return RESOURCE_FACTORIES.get( service.getRole() );
  }

  public static DeploymentFilterDescriptorFactory getClusterFilterDescriptorFactory( String filterRole ) {
    return FILTER_FACTORIES.get( filterRole );
  }
  
  public static String getClusterProviderFilterClassName(String role, String defaultClassName) {
    return ROLE_TO_FILTER_MAPPING.getProperty(role, defaultClassName);
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

  private static Map<String, DeploymentResourceDescriptorFactory> loadResourceFactories() {
    Map<String, DeploymentResourceDescriptorFactory> map = new HashMap<String, DeploymentResourceDescriptorFactory>();
    ServiceLoader<DeploymentResourceDescriptorFactory> loader = ServiceLoader.load( DeploymentResourceDescriptorFactory.class );
    Iterator<DeploymentResourceDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      DeploymentResourceDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedResourceRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }

  private static Map<String, DeploymentFilterDescriptorFactory> loadFilterFactories() {
    Map<String, DeploymentFilterDescriptorFactory> map = new HashMap<String, DeploymentFilterDescriptorFactory>();
    ServiceLoader<DeploymentFilterDescriptorFactory> loader = ServiceLoader.load( DeploymentFilterDescriptorFactory.class );
    Iterator<DeploymentFilterDescriptorFactory> factories = loader.iterator();
    while( factories.hasNext() ) {
      DeploymentFilterDescriptorFactory factory = factories.next();
      Set<String> roles = factory.getSupportedFilterRoles();
      for( String role : roles ) {
        map.put( role, factory );
      }
    }
    return map;
  }
  
  private static Properties loadRoleToFilterClassMapping() {
    InputStream inputStream = ClusterDescriptorFactory.class.getClassLoader().getResourceAsStream("META-INF/filter-provider.properties");
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
