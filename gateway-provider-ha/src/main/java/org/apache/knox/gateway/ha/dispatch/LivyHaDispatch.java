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
package org.apache.knox.gateway.ha.dispatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.livy.LivyHttpServletRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This specialized dispatch provides Livy specific features to the
 * default dispatch. It also handles active-passive HA.
 */
public class LivyHaDispatch extends ConfigurableHADispatch {

  /**
   * Wraps the request for Livy batch/session POST bodies (proxyUser) before
   * delegating to the parent HA dispatch.
   */
  @Override
  public void doPost(final URI url, final HttpServletRequest request,
      final HttpServletResponse response)
      throws IOException, URISyntaxException {
    super.doPost(url, new LivyHttpServletRequest(request), response);
  }

  /**
   * Executes the outbound request and treats HTTP 307 from Livy as a signal to
   * fail over in HA mode.
   */
  @Override
  protected void executeRequest(final HttpUriRequest outboundRequest,
      final HttpServletRequest inboundRequest,
      final HttpServletResponse outboundResponse) throws IOException {
    HttpResponse inboundResponse = null;
    try {
      inboundResponse = executeOutboundRequest(outboundRequest);

      int sc = inboundResponse.getStatusLine().getStatusCode();
      if (sc == HttpServletResponse.SC_TEMPORARY_REDIRECT) {
        inboundResponse.removeHeaders("Location");
        failoverRequest(outboundRequest,
            inboundRequest,
            outboundResponse,
            inboundResponse,
            new Exception("Livy HA redirection"));
      }

      writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse,
          inboundResponse);

    } catch (IOException e) {
      LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
      failoverRequest(outboundRequest, inboundRequest, outboundResponse,
          inboundResponse, e);
    }
  }
}
