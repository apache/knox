/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.trace;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import java.util.Set;

public class TraceHandler extends Handler.Wrapper {

  static final String HTTP_LOGGER = "org.apache.knox.gateway.http";
  static final String HTTP_REQUEST_LOGGER = HTTP_LOGGER + ".request";
  static final String HTTP_REQUEST_HEADER_LOGGER = HTTP_REQUEST_LOGGER + ".headers";
  static final String HTTP_REQUEST_BODY_LOGGER = HTTP_REQUEST_LOGGER + ".body";

  static final String HTTP_RESPONSE_LOGGER = HTTP_LOGGER + ".response";
  static final String HTTP_RESPONSE_HEADER_LOGGER = HTTP_RESPONSE_LOGGER + ".headers";
  static final String HTTP_RESPONSE_BODY_LOGGER = HTTP_RESPONSE_LOGGER + ".body";

  private Set<Integer> bodyFilter;

  public void setTracedBodyFilter( String s ) {
    bodyFilter = TraceUtil.parseIntegerSet( s );
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    Request newRequest = new TraceRequest(request);
    Response newResponse = new TraceResponse(request, response, bodyFilter);
    return super.handle(newRequest, newResponse, callback);
  }

}
