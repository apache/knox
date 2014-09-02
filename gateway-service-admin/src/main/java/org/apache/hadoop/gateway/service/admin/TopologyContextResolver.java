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

package org.apache.hadoop.gateway.service.admin;

import org.apache.hadoop.gateway.topology.Topology;

import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.persistence.jaxb.JAXBContextProperties;

public class TopologyContextResolver implements ContextResolver<JAXBContext> {

  private JAXBContext context = null;
  private String source = null;

  public TopologyContextResolver(){}

  protected TopologyContextResolver( String source ) {
    this.source = source;
  }

  public JAXBContext getContext( Class<?> type ) {
    if ( context == null ) {
      try {
        Map<String, Object> properties = new HashMap<String, Object>(1);
        properties.put( JAXBContextProperties.OXM_METADATA_SOURCE, source );
        context = JAXBContext.newInstance( new Class[] { Topology.class }, properties );
      } catch ( JAXBException e ) {
        throw new RuntimeException ( e );
      }
    }
    return context;
  }
}
