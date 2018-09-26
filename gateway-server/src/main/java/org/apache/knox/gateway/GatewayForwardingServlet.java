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
package org.apache.knox.gateway;

import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GatewayForwardingServlet extends HttpServlet{

  private static final long serialVersionUID = 1L;

  private static final String AUDIT_ACTION = "forward";

  private static final GatewayResources RES = ResourcesFactory.get( GatewayResources.class );
  private static final GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );

  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = AuditServiceFactory.getAuditService()
          .getAuditor( AuditConstants.DEFAULT_AUDITOR_NAME,
                  AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );

  private String redirectToContext = null;

  @Override
  protected void doHead(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doPut(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doGet(req, resp);
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    redirectToContext = config.getInitParameter("redirectTo");
  }

  public void doGet(HttpServletRequest request,
                    HttpServletResponse response)
            throws ServletException, IOException
  {
    String origPath = getRequestPath( request );
    try {
      auditService.createContext();

      String origRequest = getRequestLine( request );

      auditor.audit(
              AUDIT_ACTION, origPath, ResourceType.URI,
              ActionOutcome.UNAVAILABLE, RES.forwardToDefaultTopology( request.getMethod(), redirectToContext ) );

      // Perform cross context dispatch to the configured topology context
      ServletContext ctx = getServletContext().getContext(redirectToContext);
      RequestDispatcher dispatcher = ctx.getRequestDispatcher(origRequest);

      dispatcher.forward(request, response);

      auditor.audit(
              AUDIT_ACTION, origPath, ResourceType.URI,
              ActionOutcome.SUCCESS, RES.responseStatus( response.getStatus() ) );

    } catch( ServletException | IOException | RuntimeException e ) {
      auditor.audit(
              AUDIT_ACTION, origPath, ResourceType.URI,
              ActionOutcome.FAILURE );
      throw e;
    } catch( Throwable e ) {
      auditor.audit(
              AUDIT_ACTION, origPath, ResourceType.URI,
              ActionOutcome.FAILURE );
      throw new ServletException(e);
    } finally {
      auditService.detachContext();
    }
  }

  private static final String getRequestPath( final HttpServletRequest request ) {
    final String path = request.getPathInfo();
    if( path == null ) {
      return "";
    } else {
      return path;
    }
  }

  private static final String getRequestLine( final HttpServletRequest request ) {
    final String path = getRequestPath( request );
    final String query = request.getQueryString();
    if( query == null ) {
      return path;
    } else {
      return path + "?" + query;
    }
  }

}