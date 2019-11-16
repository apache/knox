/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.dispatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * An adapter class that delegate calls to {@link org.apache.knox.gateway.dispatch.NiFiDispatch}
 * for backwards compatibility with package structure.
 *
 * @since 0.14.0
 * @deprecated Use {@link org.apache.knox.gateway.dispatch.NiFiDispatch}
 */
@Deprecated
public class NiFiDispatch extends org.apache.knox.gateway.dispatch.NiFiDispatch {
  @Override
  protected void executeRequest(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
      throws IOException {
    super.executeRequest(outboundRequest, inboundRequest, outboundResponse);
  }

  @Override
  protected void writeOutboundResponse(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest, HttpServletResponse outboundResponse,
      HttpResponse inboundResponse) throws IOException {
    super.writeOutboundResponse(outboundRequest, inboundRequest,
        outboundResponse, inboundResponse);
  }
}
