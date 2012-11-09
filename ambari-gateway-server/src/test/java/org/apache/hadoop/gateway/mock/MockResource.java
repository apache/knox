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
package org.apache.hadoop.gateway.mock;

import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class MockResource {

  public static Queue<MockRequestMatcher> requests = new LinkedList<MockRequestMatcher>();
  public static Queue<Response> responses = new LinkedList<Response>();

  public static class Request {
    public HttpServletRequest httpRequest;
    public Map<String,String> params;
    public byte[] entity;

    public Request ( HttpServletRequest httpServletRequest ) {
      try {
        this.entity = IOUtils.toByteArray( httpServletRequest.getInputStream() );
      } catch( IOException e ) {
        this.entity = null;
      }
      this.httpRequest = httpServletRequest;
    }
  }

  @GET
  @Path( "/__test__")
  @Produces( MediaType.TEXT_PLAIN )
  public String get() {
    return "__HELLO__";
  }

}
