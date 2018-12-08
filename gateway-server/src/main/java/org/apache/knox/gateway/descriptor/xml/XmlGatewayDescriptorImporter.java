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
package org.apache.knox.gateway.descriptor.xml;

import org.apache.commons.digester3.Digester;
import org.apache.commons.digester3.ExtendedBaseRules;
import org.apache.commons.digester3.binder.DigesterLoader;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptorImporter;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Reader;

import static org.apache.commons.digester3.binder.DigesterLoader.newLoader;

public class XmlGatewayDescriptorImporter implements GatewayDescriptorImporter {

  private static DigesterLoader loader = newLoader( new XmlGatewayDescriptorRules() );

  @Override
  public String getFormat() {
    return "xml";
  }

  @Override
  public GatewayDescriptor load( Reader reader ) throws IOException {
    Digester digester = loader.newDigester( new ExtendedBaseRules() );
    digester.setValidating( false );
    try {
      return digester.parse( reader );
    } catch( SAXException e ) {
      throw new IOException( e );
    }
  }
}
