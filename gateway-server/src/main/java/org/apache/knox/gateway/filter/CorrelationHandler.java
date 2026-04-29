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

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationContext;
import org.apache.logging.log4j.CloseableThreadContext;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

public class CorrelationHandler extends Handler.Wrapper {
  public static final String REQUEST_ID_HEADER_NAME = "X-Request-Id";
  public static final String TRACE_ID = "trace_id";

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
    /* If request contains X-Request-Id header use it else use random uuid as correlation id */
    final String requestIdHeaderValue = request.getHeaders().get(REQUEST_ID_HEADER_NAME);
    final String reqID = StringUtils.isBlank(requestIdHeaderValue) ?
        UUID.randomUUID().toString() :
        requestIdHeaderValue;

    CorrelationContext context = new Log4jCorrelationContext(reqID, null, null);

    correlationService.attachContext(context);
    try(CloseableThreadContext.Instance ctc = CloseableThreadContext.put(TRACE_ID, reqID)) {
      return super.handle(request, response, callback);
    } finally {
      correlationService.detachContext();
    }
  }

}
