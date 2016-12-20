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

import org.apache.hadoop.gateway.service.admin.beans.Topology;

import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.*;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.Marshaller;

@Provider
@Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class TopologyMarshaller implements MessageBodyWriter<Topology>, MessageBodyReader<Topology> {

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return (Topology.class == type);
  }

  @Override
  public long getSize(Topology instance, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(Topology instance, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
    try {
      Map<String, Object> properties = new HashMap<String, Object>(1);
      properties.put( JAXBContextProperties.MEDIA_TYPE, mediaType.toString());
      JAXBContext context = JAXBContext.newInstance(new Class[]{Topology.class}, properties);
      Marshaller m = context.createMarshaller();
      m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      m.marshal(instance, entityStream);
    } catch (JAXBException e) {
      throw new IOException(e);
    }
  }

  ///
  ///MessageBodyReader Methods
  ///
  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    boolean readable = (type == Topology.class);
    return readable;
  }

  @Override
  public Topology readFrom(Class<Topology> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    try {
      if(isReadable(type, genericType, annotations, mediaType)) {
        Map<String, Object> properties = Collections.EMPTY_MAP;
        JAXBContext context = JAXBContext.newInstance(new Class[]{Topology.class}, properties);
        InputStream is = entityStream;
        Unmarshaller u = context.createUnmarshaller();
        u.setProperty(UnmarshallerProperties.MEDIA_TYPE, mediaType.getType() + "/" + mediaType.getSubtype());
        Topology topology = (Topology)u.unmarshal(is);
        return topology;
      }
    } catch (JAXBException e) {
      throw new IOException(e);
    }
    return null;
  }

}
