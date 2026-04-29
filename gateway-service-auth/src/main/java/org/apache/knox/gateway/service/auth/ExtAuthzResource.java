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
package org.apache.knox.gateway.service.auth;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path(ExtAuthzResource.RESOURCE_PATH)
public class ExtAuthzResource extends AbstractAuthResource {
  static final String RESOURCE_PATH = "auth/api/v1/extauthz";
  static final String IGNORE_ADDITIONAL_PATH = "ignore.additional.path";
  static final String DEFAULT_IGNORE_ADDITIONAL_PATH = "false";

  private boolean ignoreAdditionalPath;

  @Context
  HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    initialize();
    ignoreAdditionalPath = Boolean.parseBoolean(getInitParameter(IGNORE_ADDITIONAL_PATH, DEFAULT_IGNORE_ADDITIONAL_PATH));
  }

  @Override
  HttpServletResponse getResponse() {
    return response;
  }

  @Override
  ServletContext getContext() {
    return context;
  }

  /*
  Enable all the http verbs,
  wish there was a way to specify multiple paths on the same method
  */
  @GET
  public Response doGet() {
    return doGetImpl();
  }

  @POST
  public Response doPostWithRootPath() {
    return doGetImpl();
  }

  @PUT
  public Response doPutWithRootPath() {
    return doGetImpl();
  }

  @DELETE
  public Response doDeleteWithRootPath() {
    return doGetImpl();
  }

  @HEAD
  public Response doHeadWithPath() {
    return doGetImpl();
  }

  @OPTIONS
  public Response doOptionsWithPath() {
    return doGetImpl();
  }

  @PATCH
  public Response doPatchWithPath() {
    return doGetImpl();
  }

  /*
   * This method will handle additional paths.
   * Currently, if there are any additional paths they are ignored.
   */
  @Path("{subResources:.*}")
  @GET
  public Response doGetWithPath(@Context UriInfo ui) {
    if(ignoreAdditionalPath) {
      LOG.pathValue(ui.getPath());
      return doGet();
    } else {
      return  Response.status(Response.Status.NOT_FOUND).build();
    }
  }

  @Path("{subResources:.*}")
  @POST
  public Response doPostWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

  @Path("{subResources: .*}")
  @PUT
  public Response doPutWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

  @Path("{subResources: .*}")
  @DELETE
  public Response doDeleteWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

  @Path("{subResources: .*}")
  @HEAD
  public Response doHeadWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

  @Path("{subResources: .*}")
  @OPTIONS
  public Response doOptionsWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

  @Path("{subResources: .*}")
  @PATCH
  public Response doPatchWithPath(@Context UriInfo ui) {
    return doGetWithPath(ui);
  }

}
