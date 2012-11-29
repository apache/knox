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

import org.apache.hadoop.gateway.descriptor.impl.ClusterDescriptorImpl;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.spi.ClusterDescriptorImporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorExporter;
import org.apache.hadoop.gateway.descriptor.xml.XmlClusterDescriptorImporter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ClusterDescriptorFactory {

  private static Map<String, ClusterDescriptorImporter> importers = createImporter();
  private static Map<String, ClusterDescriptorExporter> exporters = createExporters();

  public static ClusterDescriptor create() {
    return new ClusterDescriptorImpl();
  }

  public static ClusterDescriptor load( String format, Reader reader ) throws IOException {
    ClusterDescriptorImporter importer = importers.get( format );
    if( importer == null ) {
      throw new IllegalArgumentException( "No importer for format " + format );
    }
    return importer.load( reader );
  }

  public static void store( ClusterDescriptor descriptor, String format, Writer writer ) throws IOException {
    ClusterDescriptorExporter exporter = exporters.get( format );
    if( exporter == null ) {
      throw new IllegalArgumentException( "No exporter for format " + format );
    }
    exporter.store( descriptor, writer );
  }

  private static Map<String, ClusterDescriptorImporter> createImporter() {
    Map<String, ClusterDescriptorImporter> map = new ConcurrentHashMap<String, ClusterDescriptorImporter>();
    map.put( "xml", new XmlClusterDescriptorImporter() );
    return map;
  }

  private static Map<String, ClusterDescriptorExporter> createExporters() {
    Map<String, ClusterDescriptorExporter> map = new ConcurrentHashMap<String, ClusterDescriptorExporter>();
    map.put( "xml", new XmlClusterDescriptorExporter() );
    return map;
  }

}
