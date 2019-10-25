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
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.knox.gateway.service.definition.ServiceDefinitionPair;

@Provider
@Consumes({ MediaType.APPLICATION_XML })
public class ServiceDefinitionUnmarshaller implements MessageBodyReader<ServiceDefinitionPair> {

  private static Unmarshaller unmarshaller;

  @Override
  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return ServiceDefinitionPair.class == type;
  }

  @Override
  public ServiceDefinitionPair readFrom(Class<ServiceDefinitionPair> instance, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {
    try {
      return (ServiceDefinitionPair) getUnmarshaller().unmarshal(entityStream);
    } catch (JAXBException e) {
      throw new IOException(e);
    }
  }

  private synchronized Unmarshaller getUnmarshaller() throws JAXBException {
    if (unmarshaller == null) {
      unmarshaller = JAXBContext.newInstance(ServiceDefinitionPair.class).createUnmarshaller();
    }
    return unmarshaller;
  }
}
