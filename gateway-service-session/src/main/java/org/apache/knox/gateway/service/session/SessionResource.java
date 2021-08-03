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

package org.apache.knox.gateway.service.session;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

import javax.inject.Singleton;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.SubjectUtils;

@Singleton
@Path("session/api/v1/")
public class SessionResource {
  private static final SessionServiceMessages LOG = MessagesFactory.get(SessionServiceMessages.class);

  @Context
  HttpServletRequest request;

  @Context
  ServletContext context;

  @GET
  @Produces({ APPLICATION_JSON, APPLICATION_XML })
  @Path("sessioninfo")
  public SessionInformation getSessionInformation() {
    final SessionInformation sessionInfo = new SessionInformation();
    sessionInfo.setUser(SubjectUtils.getCurrentEffectivePrincipalName());
    final GatewayConfig config = (GatewayConfig) context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    if (config != null && config.homePageLogoutEnabled()) {
      String logoutUrl = getBaseGatewayUrl(config) + "/homepage/knoxssout/api/v1/webssout";
      LOG.homePageLogoutEnabled(logoutUrl);
      sessionInfo.setLogoutUrl(logoutUrl);
      sessionInfo.setLogoutPageUrl(getLogoutPageUrl(config));
      sessionInfo.setGlobalLogoutPageUrl(getGlobalLogoutPageUrl(config));
    }

    return sessionInfo;
  }

  private String getBaseGatewayUrl(GatewayConfig config) {
    return request.getRequestURL().substring(0,
        request.getRequestURL().length() - request.getRequestURI().length()) +
        "/" + config.getGatewayPath();
  }

  private String getLogoutPageUrl(GatewayConfig config) {
    return getBaseGatewayUrl(config) +
        "/knoxsso/knoxauth/logout.jsp?originalUrl=" + getBaseGatewayUrl(config) +
        "/homepage/home";
  }

  private String getGlobalLogoutPageUrl(GatewayConfig config) {
    return config.getGlobalLogoutPageUrl();
  }
}
