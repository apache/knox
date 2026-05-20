/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
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
package org.apache.knox.gateway.service.knoxidf;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.getRequestParamSafe;


public class AuthConsentServlet extends HttpServlet {

    @Context
    UriInfo uriInfo;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        final String clientId = getRequestParamSafe(request, "client_id");
        final String state = getRequestParamSafe(request, "state");
        final String scope = getRequestParamSafe(request, "scope");
        final Set<String> scopes = new HashSet<>(Arrays.asList(scope.split("\\s+")));

        try (PrintWriter out = response.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html><head><title>Consent Required</title>");
            out.println("<style>");
            out.println("body { font-family: 'Segoe UI', Arial, sans-serif; background: #f5f6f8; padding: 40px; margin: 0; }");
            out.println(".container { background: #fff; padding: 30px 40px; border-radius: 10px; max-width: 480px; width: 100%; box-shadow: 0 4px 12px rgba(0,0,0,0.1); margin: 0 auto; }");
            out.println("h2 { color: #333; margin-bottom: 20px; }");
            out.println("p { font-size: 15px; color: #555; margin-bottom: 20px; }");
            out.println("ul { text-align: left; padding-left: 20px; margin-bottom: 30px; }");
            out.println("li { margin-bottom: 8px; }");
            out.println("button { padding: 10px 24px; margin: 0 10px; font-size: 15px; cursor: pointer; border-radius: 6px; border: none; transition: background-color 0.25s ease; }");
            out.println(".accept { background: #007bff; color: #fff; }");
            out.println(".accept:hover { background: #0069d9; }");
            out.println(".deny { background: #e0e0e0; color: #333; }");
            out.println(".deny:hover { background: #d0d0d0; }");
            out.println("</style>");
            out.println("</head><body>");
            out.println("<div class='container'>");
            out.println("<h2>Application Consent Required</h2>");
            out.printf(Locale.US, "<p>The application <b>%s</b> is requesting access to your account.</p>%n", clientId);

            if (!scopes.isEmpty()) {
                out.println("<p>This application will be able to:</p>");
                out.println("<ul>");
                for (String s : scopes) {
                    out.printf(Locale.US, "<li>%s</li>%n", describeScope(s));
                }
                out.println("</ul>");
            }

            out.println("<form method='post' onsubmit='return confirmAction();'>");
            out.printf(Locale.US, "<input type='hidden' name='state' value='%s'/>%n", state);
            out.println("<div style='display: flex; justify-content: center; gap: 20px;'>");
            out.println("<button type='submit' name='action' value='accept' class='accept'>Accept</button>");
            out.println("<button type='submit' name='action' value='deny' class='deny'>Deny</button>");
            out.println("</div>");
            out.println("</form>");
            out.println("</div>");
            out.println("<script>");
            out.println("function confirmAction() {");
            out.println("  const action = event.submitter.value;");
            out.println("  if (action === 'accept') return confirm('Do you want to grant consent to this application?');");
            out.println("  return true;");
            out.println("}");
            out.println("</script>");
            out.println("</body></html>");
        }
    }

    private String describeScope(String scope) {
        if (scope == null) {
            return "";
        }

        switch (scope) {
            case "openid":
                return "Authenticate using your account";
            case "profile":
                return "View your basic profile information";
            case "email":
                return "View your email address";
            case "address":
                return "View your address information";
            case "phone":
                return "View your phone number";
            case "calendar.read":
                return "Read your calendar events";
            case "calendar.write":
                return "Modify your calendar events";
            default:
                return scope;
        }
    }

    //Redirect target is application-local and state is encoded/controlled
    @SuppressWarnings("UNVALIDATED_REDIRECT")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        final String action = request.getParameter("action");
        final String state = request.getParameter("state");
        final String redirectUri = request.getServletContext().getContextPath() + "/" + AuthorizeResource.RESOURCE_PATH +
                ("accept".equals(action) ? "/consentAccepted?state=" + state : "/consentDenied");
        response.sendRedirect(redirectUri);
    }

}
