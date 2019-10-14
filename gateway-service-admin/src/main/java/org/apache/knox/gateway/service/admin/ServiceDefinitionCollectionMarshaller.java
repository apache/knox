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

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.knox.gateway.service.admin.ServiceDefinitionsResource.ServiceDefinitionsWrapper;

@Provider
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public class ServiceDefinitionCollectionMarshaller implements MessageBodyWriter<ServiceDefinitionsResource.ServiceDefinitionsWrapper> {

  private static Marshaller marshaller;

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return ServiceDefinitionsResource.ServiceDefinitionsWrapper.class == type;
  }

  @Override
  public long getSize(ServiceDefinitionsWrapper t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(ServiceDefinitionsWrapper instance, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
    try {
      getMarshaller().marshal(instance, entityStream);
    } catch (JAXBException e) {
      throw new IOException(e);
    }
  }

  private synchronized Marshaller getMarshaller() throws JAXBException {
    if (marshaller == null) {
      marshaller = JAXBContext.newInstance(ServiceDefinitionsWrapper.class).createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }
    return marshaller;
  }
}
