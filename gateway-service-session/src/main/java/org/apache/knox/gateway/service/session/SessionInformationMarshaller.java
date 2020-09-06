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
package org.apache.knox.gateway.service.session;

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
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.JAXBContextProperties;

@Provider
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public class SessionInformationMarshaller implements MessageBodyWriter<SessionInformation>{
  private static Marshaller xmlMarshaller;
  private static Marshaller jsonMarshaller;

  @Override
  public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return SessionInformation.class == type;
  }

  @Override
  public long getSize(SessionInformation t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return -1;
  }

  @Override
  public void writeTo(SessionInformation instance, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
    try {
      getMarshaller(mediaType).marshal(instance, entityStream);
    } catch (JAXBException e) {
      throw new IOException(e);
    }
  }

  private Marshaller getMarshaller(MediaType mediaType) throws JAXBException {
    return MediaType.APPLICATION_JSON_TYPE.getSubtype().equals(mediaType.getSubtype()) ? getJsonMarshaller() : getXmlMarshaller();
  }

  private synchronized Marshaller getXmlMarshaller() throws JAXBException {
    if (xmlMarshaller == null) {
      final Map<String, Object> properties = new HashMap<>(1);
      properties.put(JAXBContextProperties.MEDIA_TYPE, MediaType.APPLICATION_XML);
      xmlMarshaller = JAXBContextFactory.createContext(new Class[] { SessionInformation.class }, properties).createMarshaller();
      xmlMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }
    return xmlMarshaller;
  }

  private synchronized Marshaller getJsonMarshaller() throws JAXBException {
    if (jsonMarshaller == null) {
      final Map<String, Object> properties = new HashMap<>(1);
      properties.put(JAXBContextProperties.MEDIA_TYPE, MediaType.APPLICATION_JSON);
      jsonMarshaller = JAXBContextFactory.createContext(new Class[] { SessionInformation.class }, properties).createMarshaller();
      jsonMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
    }
    return jsonMarshaller;
  }

}
