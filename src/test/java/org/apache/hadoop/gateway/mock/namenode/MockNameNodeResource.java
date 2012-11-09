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
package org.apache.hadoop.gateway.mock.namenode;

import org.apache.hadoop.gateway.mock.MockResource;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@Path( "webhdfs/v1" )
public class MockNameNodeResource extends MockResource {

  @GET
  @Path( "/{path}" )
  public Response get(
      @Context HttpServletRequest request,
      @PathParam("path") String path ) throws IOException {
    requests.remove().match( request );
    return responses.remove();
  }

  @PUT
  @Path( "/{path}" )
  public Response put(
      @Context HttpServletRequest request,
      @PathParam("path") String path,
      @QueryParam("op") String operation ) {
    assertThat( operation, anyOf( equalTo( "MKDIRS" ), equalTo( "CREATE" ) ) );
    //interactions.add( new Request( request ) );
    return responses.remove();
  }

  @DELETE
  @Path( "/{path}" )
  public Response delete(
      @Context HttpServletRequest request,
      @PathParam("path") String path,
      @QueryParam("op") String operation ) {
    assertThat( operation, equalTo( "DELETE" ) );
    //interactions.add( new Request( request ) );
    return responses.remove();
  }

}
