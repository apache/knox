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
package org.apache.knox.gateway.service.idbroker;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Properties;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(IdentityBrokerResource.RESOURCE_PATH)
public class IdentityBrokerResource {
  private static final String CREDENTIALS_API_PATH = "credentials";
  private static final String USER_CREDENTIALS_API_PATH = "credentials/{id}";
  private static IdBrokerServiceMessages log = MessagesFactory.get(IdBrokerServiceMessages.class);
  private static final String VERSION_TAG = "api/v1";
  static final String RESOURCE_PATH = "/idbroker/" + VERSION_TAG;

  private static final String CONTENT_TYPE = "application/json";
  private static final String CACHE_CONTROL = "Cache-Control";
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";

  private KnoxCloudPolicyProvider policyProvider = new KnoxPolicyProviderManager();
  private KnoxCloudCredentialsClient credentialsClient = new KnoxCloudCredentiatlsClientManager();

  @Context
  HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    Properties props = getProperties();
    policyProvider.init(props);
    credentialsClient.init(props);
    credentialsClient.setPolicyProvider(policyProvider);
  }

  private Properties getProperties() {
    Properties props = new Properties();
    String paramName = null;
    Enumeration<String> e = context.getInitParameterNames();
    while (e.hasMoreElements()) {
      paramName = (String)e.nextElement();
      props.setProperty(paramName, context.getInitParameter(paramName));
    }
    
    return props;
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(CREDENTIALS_API_PATH)
  public Response getCredentials() {
    return getCredentialsResponse();
  }

  @GET
  @Produces({APPLICATION_JSON})
  @Path(USER_CREDENTIALS_API_PATH)
  public Response getUserCredentials() {
    return getCredentialsResponse();
  }

  private Response getCredentialsResponse() {
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      writer.println(getFederationToken().toString());
    } catch (Exception e) {
      log.logException("list", e);
      return Response.serverError().entity(String.format("Failed to reply correctly due to : %s ", e)).build();
    } finally {
      if (writer != null) {
        try {
          writer.close();
        }
        catch (Exception e) {
          // NOP
        }
      }
    }
    return Response.ok().build();
  }

  protected String getFederationToken() {
//    Subject subject = Subject.getSubject(AccessController.getContext());
//    String username = getEffectiveUserName(subject);
    
    // TODO: make sure that the toString behavior is polymorphic here
    // we have to avoid any cloud vendor specific casting here
    Object creds = credentialsClient.getCredentials();
    return creds.toString();
  }
}
