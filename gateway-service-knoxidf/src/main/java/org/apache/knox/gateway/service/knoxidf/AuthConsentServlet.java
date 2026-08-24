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

import org.apache.commons.text.StringEscapeUtils;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
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

            // Accept/deny POST directly to the JAX-RS consent endpoints (which require POST) via each
            // button's formaction, so accepting consent is never triggerable by a passive GET (prefetch,
            // history re-nav, a leaked consent-state URL). The base path is derived from the servlet
            // context and a compile-time constant, so it needs no escaping.
            final String consentBasePath = request.getServletContext().getContextPath() + "/" + AuthorizeResource.RESOURCE_PATH;
            out.println("<form method='post' onsubmit='return confirmAction();'>");
            // Render state in a double-quoted attribute: getRequestParamSafe escapes via escapeHtml4,
            // which encodes '"' (&quot;) but NOT a single quote, so a single-quoted attribute here
            // would let an attacker-supplied state break out of the attribute and inject markup.
            out.printf(Locale.US, "<input type=\"hidden\" name=\"state\" value=\"%s\"/>%n", state);
            out.println("<div style='display: flex; justify-content: center; gap: 20px;'>");
            out.printf(Locale.US, "<button type='submit' name='action' value='accept' class='accept' formaction=\"%s/consentAccepted\">Accept</button>%n", consentBasePath);
            out.printf(Locale.US, "<button type='submit' name='action' value='deny' class='deny' formaction=\"%s/consentDenied\">Deny</button>%n", consentBasePath);
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
                // Unknown scopes are echoed into the HTML consent page. Escape them so an
                // attacker-influenced scope value cannot inject markup (defense in depth).
                return StringEscapeUtils.escapeHtml4(scope);
        }
    }

}
