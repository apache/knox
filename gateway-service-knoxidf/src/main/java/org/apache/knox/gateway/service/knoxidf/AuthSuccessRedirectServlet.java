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
import java.io.IOException;
import java.io.PrintWriter;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFUtils.getRequestParamSafe;

public class AuthSuccessRedirectServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");

        final String code = getRequestParamSafe(req, "code");
        final String state = getRequestParamSafe(req, "state");

        try (PrintWriter out = resp.getWriter()) {
            out.println("<!DOCTYPE html>");
            out.println("<html lang='en'>");
            out.println("<head>");
            out.println("  <meta charset='UTF-8'/>");
            out.println("  <title>Authentication Success</title>");
            out.println("  <style>");
            out.println("    body { font-family: Arial, sans-serif; background-color: #f4f4f4; text-align: center; padding-top: 100px; }");
            out.println("    .container { background: white; display: inline-block; padding: 40px; border-radius: 10px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); }");
            out.println("    .code { font-size: 20px; margin: 20px 0; color: #333; display: inline-block; padding: 10px 20px; background: #eee; border-radius: 5px; }");
            out.println("    button { background-color: #0078d7; color: white; border: none; border-radius: 5px; padding: 10px 20px; cursor: pointer; font-size: 14px; }");
            out.println("    button:hover { background-color: #005ea6; }");
            out.println("    #message { color: green; margin-top: 15px; font-size: 14px; display: none; }");
            out.println("  </style>");
            out.println("</head>");
            out.println("<body>");
            out.println("  <div class='container'>");
            out.println("    <h2>✅ Authentication Successful!</h2>");
            out.println("    <p>Your authorization code:</p>");
            out.println("    <div class='code' id='authCode'>" + code + "</div><br/>");
            out.println("    <button onclick='copyCode()'>📋 Copy Code</button>");
            out.println("    <div id='message'>Copied to clipboard!</div>");
            if (state != null) {
                out.println("    <hr />");
                out.println("    <p>State:</p>");
                out.println("    <div class='code' id='state'>" + state + "</div>");
            }
            out.println("  </div>");
            out.println("  <script>");
            out.println("    function copyCode() {");
            out.println("      const codeElem = document.getElementById('authCode');");
            out.println("      const text = codeElem.textContent;");
            out.println("      if (navigator.clipboard && window.isSecureContext) {");
            out.println("        navigator.clipboard.writeText(text).then(showMsg);");
            out.println("      } else {");
            out.println("        const textarea = document.createElement('textarea');");
            out.println("        textarea.value = text;");
            out.println("        textarea.style.position = 'fixed';");
            out.println("        textarea.style.left = '-9999px';");
            out.println("        document.body.appendChild(textarea);");
            out.println("        textarea.focus();");
            out.println("        textarea.select();");
            out.println("        try { document.execCommand('copy'); showMsg(); } catch (err) { console.error('Copy failed', err); }");
            out.println("        document.body.removeChild(textarea);");
            out.println("      }");
            out.println("    }");
            out.println("    function showMsg() {");
            out.println("      const msg = document.getElementById('message');");
            out.println("      msg.style.display = 'block';");
            out.println("      setTimeout(() => msg.style.display = 'none', 2000);");
            out.println("    }");
            out.println("  </script>");
            out.println("</body>");
            out.println("</html>");
        }
    }

}
