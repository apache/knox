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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.knox.gateway.service.admin.ServiceDiscoveryResource.ServiceDiscoveryWrapper;
import org.eclipse.persistence.jaxb.JAXBContextProperties;

@Provider
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class ServiceDiscoveryCollectionMarshaller implements MessageBodyWriter<ServiceDiscoveryResource.ServiceDiscoveryWrapper> {

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return ServiceDiscoveryResource.ServiceDiscoveryWrapper.class == type;
  }

  @Override
  public long getSize(ServiceDiscoveryWrapper t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(ServiceDiscoveryResource.ServiceDiscoveryWrapper instance, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
    try {
      final Map<String, Object> properties = new HashMap<>(1);
      properties.put(JAXBContextProperties.MEDIA_TYPE, mediaType.toString());
      final JAXBContext context = JAXBContext.newInstance(new Class[] { ServiceDiscoveryResource.ServiceDiscoveryWrapper.class }, properties);
      final Marshaller m = context.createMarshaller();
      m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
      m.marshal(instance, entityStream);
    } catch (JAXBException e) {
      throw new IOException(e);
    }
  }

}
