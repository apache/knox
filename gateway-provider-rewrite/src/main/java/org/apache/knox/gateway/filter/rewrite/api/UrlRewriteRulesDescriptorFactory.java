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
package org.apache.knox.gateway.filter.rewrite.api;

import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteResources;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRulesDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.impl.xml.XmlUrlRewriteRulesExporter;
import org.apache.knox.gateway.filter.rewrite.impl.xml.XmlUrlRewriteRulesImporter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteRulesExporter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteRulesImporter;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class UrlRewriteRulesDescriptorFactory {

  private static UrlRewriteResources RES = ResourcesFactory.get( UrlRewriteResources.class );

  private static Map<String, UrlRewriteRulesImporter> IMPORTERS = loadImporters();
  private static Map<String, UrlRewriteRulesExporter> EXPORTERS = loadExporters();

  private UrlRewriteRulesDescriptorFactory() {
  }

  public static UrlRewriteRulesDescriptor create() {
    return new UrlRewriteRulesDescriptorImpl();
  }

  public static UrlRewriteRulesDescriptor load( String format, Reader reader ) throws IOException {
    UrlRewriteRulesImporter importer = IMPORTERS.get( format );
    if( importer == null ) {
      throw new IllegalArgumentException( RES.noImporterForFormat( format ) );
    }
    return importer.load( reader );
  }

  public static void store( UrlRewriteRulesDescriptor descriptor, String format, Writer writer ) throws IOException {
    UrlRewriteRulesExporter exporter = EXPORTERS.get( format );
    if( exporter == null ) {
      throw new IllegalArgumentException( RES.noExporterForFormat( format ) );
    }
    exporter.store( descriptor, writer );
  }

  private static Map<String, UrlRewriteRulesImporter> loadImporters() {
    Map<String, UrlRewriteRulesImporter> map = new ConcurrentHashMap<String, UrlRewriteRulesImporter>();
    map.put( "xml", new XmlUrlRewriteRulesImporter() );
    return map;
  }

  private static Map<String, UrlRewriteRulesExporter> loadExporters() {
    Map<String, UrlRewriteRulesExporter> map = new ConcurrentHashMap<String, UrlRewriteRulesExporter>();
    map.put( "xml", new XmlUrlRewriteRulesExporter() );
    return map;
  }

}
