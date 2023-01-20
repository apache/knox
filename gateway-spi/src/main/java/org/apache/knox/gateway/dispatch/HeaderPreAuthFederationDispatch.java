/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.dispatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.config.GatewayConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;

/**
 * This dispatch should be used for federating multiple
 * Knox instances. This dispatch will add the authentication header,
 * which can be set using the property
 * gateway.custom.federation.header.name
 * in gateway-site.xml. The value of the header will be
 * authenticated principal.
 * Authentication provider configured in topology will be used to authenticate.
 * The receiving Knox instance will need to have Header PreAuth
 * provider configured to accept the requests.
 *
 * @since 1.1.0
 */
public class HeaderPreAuthFederationDispatch extends ConfigurableDispatch {

  String headerName = "SM_USER";

  /* Create an instance */
  public HeaderPreAuthFederationDispatch() {
    super();
  }

  @Override
  protected void executeRequest(
      final HttpUriRequest outboundRequest,
      final HttpServletRequest inboundRequest,
      final HttpServletResponse outboundResponse)
      throws IOException {

    final GatewayConfig config =
        (GatewayConfig)inboundRequest.getServletContext().getAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE );

    if(config != null && config.getFederationHeaderName() != null) {
      headerName = config.getFederationHeaderName();
    }

    final Principal principal = inboundRequest.getUserPrincipal();
    if(principal != null) {
      outboundRequest.addHeader(headerName, principal.getName());
    }

    final HttpResponse inboundResponse = executeOutboundRequest(outboundRequest);
    writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
  }
}
