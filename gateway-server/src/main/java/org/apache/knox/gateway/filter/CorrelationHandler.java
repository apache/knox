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
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.api.CorrelationServiceFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

public class CorrelationHandler extends HandlerWrapper {

  @Override
  public void handle( String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException {
    CorrelationService correlationService = CorrelationServiceFactory.getCorrelationService();
    CorrelationContext correlationContext = correlationService.createContext();
    correlationContext.setRequestId( UUID.randomUUID().toString() );
    try {
      super.handle( target, baseRequest, request, response );
    } finally {
      // Ensure that the correlationContext is destroyed between requests
      correlationContext.destroy();
      correlationService.detachContext();
    }
  }
}
