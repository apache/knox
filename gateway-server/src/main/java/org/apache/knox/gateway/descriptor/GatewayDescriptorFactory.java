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

import org.apache.knox.gateway.descriptor.impl.GatewayDescriptorImpl;
import org.apache.knox.gateway.descriptor.xml.XmlGatewayDescriptorExporter;
import org.apache.knox.gateway.descriptor.xml.XmlGatewayDescriptorImporter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class GatewayDescriptorFactory {

  private static Map<String, GatewayDescriptorImporter> IMPORTERS = loadImporters();
  private static Map<String, GatewayDescriptorExporter> EXPORTERS = loadExporters();

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

  private static Map<String, GatewayDescriptorImporter> loadImporters() {
    Map<String, GatewayDescriptorImporter> map = new ConcurrentHashMap<>();
    map.put( "xml", new XmlGatewayDescriptorImporter() );
    return map;
  }

  private static Map<String, GatewayDescriptorExporter> loadExporters() {
    Map<String, GatewayDescriptorExporter> map = new ConcurrentHashMap<>();
    map.put( "xml", new XmlGatewayDescriptorExporter() );
    return map;
  }

}
