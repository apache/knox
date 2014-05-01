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
package org.apache.hadoop.gateway;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

public class GatewayForwardingServlet extends HttpServlet{

  private static final long serialVersionUID = 1L;  
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
    String path = "";
    String pathInfo = request.getPathInfo();
    if (pathInfo != null && pathInfo.length() > 0) {
      path = path + pathInfo;
    }
    String qstr =  request.getQueryString();
    if (qstr != null && qstr.length() > 0) {
      path = path + "?" + qstr;
    }

    // Perform cross context dispatch to the configured topology context
    ServletContext ctx = getServletContext().getContext(redirectToContext);
    RequestDispatcher dispatcher = ctx.getRequestDispatcher(path);
    dispatcher.forward(request, response);    
  }

  public static class MyRequest extends HttpServletRequestWrapper {
    private String redirectTo = null;
    
    public MyRequest(HttpServletRequest request, String redirectTo) {
        super(request);
    }

    @Override    
    public String getContextPath() {
        return redirectTo;
    }

  }

} 