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

import org.apache.knox.gateway.service.admin.beans.Topology;
import org.eclipse.persistence.jaxb.JAXBContextProperties;
import org.eclipse.persistence.jaxb.UnmarshallerProperties;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
  public void writeTo(Topology                       instance,
                      Class<?>                       type,
                      Type                           genericType,
                      Annotation[]                   annotations,
                      MediaType                      mediaType,
                      MultivaluedMap<String, Object> httpHeaders,
                      OutputStream                   entityStream) throws IOException, WebApplicationException {
    try {
      Map<String, Object> properties = new HashMap<>(1);
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
    return (type == Topology.class);
  }

  @Override
  public Topology readFrom(Class<Topology>                type,
                           Type                           genericType,
                           Annotation[]                   annotations,
                           MediaType                      mediaType,
                           MultivaluedMap<String, String> httpHeaders,
                           InputStream                    entityStream) throws IOException, WebApplicationException {
    Topology topology = null;

    try {
      if (isReadable(type, genericType, annotations, mediaType)) {
        Map<String, Object> properties = Collections.emptyMap();
        JAXBContext context = JAXBContext.newInstance(new Class[]{Topology.class}, properties);

        Unmarshaller u = context.createUnmarshaller();
        u.setProperty(UnmarshallerProperties.MEDIA_TYPE, mediaType.getType() + "/" + mediaType.getSubtype());

        if (mediaType.isCompatible(MediaType.APPLICATION_XML_TYPE)) {
          // Safeguard against entity injection (KNOX-1308)
          XMLInputFactory xif = XMLInputFactory.newFactory();
          xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
          xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
          xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
          XMLStreamReader xsr = xif.createXMLStreamReader(new StreamSource(entityStream));
          topology = (Topology) u.unmarshal(xsr);
        } else {
          topology = (Topology) u.unmarshal(entityStream);
        }
      }
    } catch (XMLStreamException | JAXBException e) {
      throw new IOException(e);
    }

    return topology;
  }

}
