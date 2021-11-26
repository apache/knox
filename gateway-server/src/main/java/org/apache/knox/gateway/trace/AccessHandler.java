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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

public class AccessHandler extends AbstractLifeCycle implements RequestLog {
  private static final Logger log = LogManager.getLogger( "org.apache.knox.gateway.access" );

  @Override
  public void log( Request request, Response response ) {
    if( log.isTraceEnabled() ) {
      StringBuilder sb = new StringBuilder();
      TraceUtil.appendCorrelationContext(sb);
      sb.append('|')
          .append(request.getRemoteAddr())
          .append('|')
          .append(request.getMethod())
          .append('|')
          .append(request.getHttpURI())
          .append('|')
          .append(request.getContentLength())
          .append('|')
          .append(response.getStatus())
          .append('|')
          .append(response.getContentCount())
          .append('|')
          .append(System.currentTimeMillis() - request.getTimeStamp());
      log.trace(sb);
    }
  }
}
