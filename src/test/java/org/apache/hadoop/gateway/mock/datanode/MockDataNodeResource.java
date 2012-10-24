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
package org.apache.hadoop.gateway.mock.datanode;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path( "webhdfs" )
public class MockDataNodeResource {

  @GET
  @Path( "/hello")
  @Produces( MediaType.TEXT_PLAIN )
  public String get() {
    return "HELLO";
  }

  @GET
  @Path( "/{version}/{file}")
  @Produces( MediaType.APPLICATION_JSON )
  public String get(
      @QueryParam("op") String operation,
      @PathParam( "version" ) String version,
      @PathParam( "file" ) String file ) {
    return "{}";
  }

}
