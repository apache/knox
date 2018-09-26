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
package org.apache.knox.gateway.service.health;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;


@Path(PingResource.RESOURCE_PATH)
public class PingResource {
  static final String VERSION_TAG = "v1";
  static final String RESOURCE_PATH = "/" + VERSION_TAG + "/ping";
  public static final String CONTENT = "OK";
  private static HealthServiceMessages log = MessagesFactory.get(HealthServiceMessages.class);
  private static final String CONTENT_TYPE = "text/plain";
  private static final String CACHE_CONTROL = "Cache-Control";
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";

  @Context
  HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @GET
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  public Response doGet() {
    return getPingResponse();
  }

  @POST
  @Produces({APPLICATION_JSON, TEXT_PLAIN})
  public Response doPost() {
    return getPingResponse();
  }

  private Response getPingResponse() {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setHeader(CACHE_CONTROL, NO_CACHE);
    response.setContentType(CONTENT_TYPE);
    PrintWriter writer = null;
    try {
      writer = response.getWriter();
      writer.println(getPingContent());
    } catch (IOException ioe) {
      log.logException("ping", ioe);
      return Response.serverError().entity(String.format(Locale.ROOT, "Failed to reply correctly due to : %s ", ioe)).build();
    } finally {
      if (writer != null) {
        writer.close();
      }
    }
    return Response.ok().build();
  }

  String getPingContent() {
    return CONTENT;
  }

}
