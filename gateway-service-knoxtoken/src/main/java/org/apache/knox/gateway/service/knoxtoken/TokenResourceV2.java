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
package org.apache.knox.gateway.service.knoxtoken;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * V2 of the public KNOXTOKEN API with the following changes:
 * <ul>
 * <li><code>renew</code> is now a <code>PUT</code> request (in V1 it was
 * <code>POST</code>)</li>
 * <li><code>revoke</code> is now a <code>DELETE</code> request (in V1, it was a
 * <code>POST</code>)</li>
 * </ul>
 *
 */
@Singleton
@Path(TokenResourceV2.RESOURCE_PATH)
public class TokenResourceV2 extends TokenResource {

  static final String RESOURCE_PATH = "knoxtoken/api/v2/token";

  // REST endpoints with the same HTTP method

  @Override
  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  public Response doGet() {
    return super.doGet();
  }

  @Override
  @POST
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  public Response doPost() {
    return super.doPost();
  }

  @Override
  @GET
  @Path(GET_TSS_STATUS_PATH)
  @Produces({ APPLICATION_JSON })
  public Response getTokenStateServiceStatus() {
    return super.getTokenStateServiceStatus();
  }

  @Override
  @GET
  @Path(GET_USER_TOKENS)
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  public Response getUserTokens(@Context UriInfo uriInfo) {
    return super.getUserTokens(uriInfo);
  }

  @Override
  @DELETE
  @Path(BATCH_REVOKE_PATH)
  @Produces({ APPLICATION_JSON })
  public Response revokeTokens(String tokenIds) {
    return super.revokeTokens(tokenIds);
  }

  @Override
  @PUT
  @Path(ENABLE_PATH)
  @Produces({ APPLICATION_JSON })
  public Response enable(String tokenId) {
    return super.enable(tokenId);
  }

  @Override
  @PUT
  @Path(BATCH_ENABLE_PATH)
  @Consumes({ APPLICATION_JSON })
  @Produces({ APPLICATION_JSON })
  public Response enableTokens(String tokenIds) {
    return super.enableTokens(tokenIds);
  }

  @Override
  @PUT
  @Path(DISABLE_PATH)
  @Produces({ APPLICATION_JSON })
  public Response disable(String tokenId) {
    return super.disable(tokenId);
  }

  @Override
  @PUT
  @Path(BATCH_DISABLE_PATH)
  @Consumes({ APPLICATION_JSON })
  @Produces({ APPLICATION_JSON })
  public Response disableTokens(String tokenIds) {
    return super.disableTokens(tokenIds);
  }

  // REST endpoints where the HTTP method changed

  @Override
  @SuppressWarnings("deprecation")
  @PUT
  @Path(RENEW_PATH)
  public Response renew(String token) {
    return super.renew(token);
  }

  @Override
  @SuppressWarnings("deprecation")
  @DELETE
  @Path(REVOKE_PATH)
  @Produces({ APPLICATION_JSON })
  public Response revoke(String token) {
    return super.revoke(token);
  }

}
