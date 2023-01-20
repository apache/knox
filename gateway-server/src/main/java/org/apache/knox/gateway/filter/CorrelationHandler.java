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
package org.apache.knox.gateway.filter;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.logging.log4j.CloseableThreadContext;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

public class CorrelationHandler extends HandlerWrapper {
  public static final String REQUEST_ID_HEADER_NAME = "X-Request-Id";
  public static final String TRACE_ID = "trace_id";

  @Override
  public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException {
    CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
    /* If request contains X-Request-Id header use it else use random uuid as correlation id */
    final String reqID =
        StringUtils.isBlank(request.getHeader(REQUEST_ID_HEADER_NAME)) ?
            UUID.randomUUID().toString() :
            request.getHeader(REQUEST_ID_HEADER_NAME);

    correlationService.attachContext(
            new Log4jCorrelationContext(reqID,
                null, null));
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, reqID)) {
      super.handle( target, baseRequest, request, response );
    } finally {
      correlationService.detachContext();
    }
  }
}
