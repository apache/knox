<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.apache.knox.gateway.topology.Topology" %>
<%@ page import="org.apache.knox.gateway.topology.Service" %>
<%@ page import="org.apache.knox.gateway.util.WhitelistUtils" %>
<%@ page import="org.apache.knox.gateway.config.GatewayConfig" %>
<%@ page import="org.apache.knox.gateway.util.Urls" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>

<!DOCTYPE html>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <title>KnoxSSO - Session Termination</title>
        <meta name="description" content="">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Type" content="text/html;charset=utf-8"/>
        <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
        <meta http-equiv="Pragma" content="no-cache">
        <meta http-equiv="Expires" content="0">

        <link rel="shortcut icon" href="images/favicon.ico">
        <!-- Bootstrap 5.3.3 -->
        <link href="styles/bootstrap.min.css" media="all" rel="stylesheet" type="text/css" id="bootstrap-css">
        <!-- Bootstrap Icons 1.11.3 -->
        <link href="styles/bootstrap-icons.min.css" media="all" rel="stylesheet" type="text/css">
        <link href="styles/knox.css" media="all" rel="stylesheet" type="text/css" >

        <script type="text/javascript" src="theme-config.js"></script>
        <script type="text/javascript" src="js/knox-theme.js"></script>

        <script src="libs/bower/jquery/js/jquery-3.5.1.min.js" ></script>

        <script type="text/javascript" src="js/knoxauth.js"></script>

        <script type="text/javascript">
           $(function() {
                var updateBoxPosition = function() {
                    $('#signout-container').css({
                        'margin-top' : ($(window).height() - $('#signout-container').height()) / 2
                    });
                };
                $(window).resize(updateBoxPosition);
                setTimeout(updateBoxPosition, 50);
            });
        </script>

       <%
       final boolean autoGlobalLogout = "1".equals(request.getParameter("autoGlobalLogout"));
       if (autoGlobalLogout) {%>
           <script type="text/javascript">
              window.onload=function() {
                  window.setTimeout(document.getElementById("globalLogoutForm").submit(), 10);
              };
          </script>
       <%}%>

    <%
        String originalUrl = request.getParameter("originalUrl");
        if (originalUrl == null) {
            originalUrl = "";
        }
        originalUrl = originalUrl.replaceAll("&", "%26");
        String themeParam = request.getParameter("theme");
        // Keep pattern in sync with KNOX_THEME_NAME_PATTERN in theme-config.js
        if (themeParam != null && !themeParam.matches("^[a-zA-Z0-9_-]{1,64}$")) {
            themeParam = null;
        }
        Topology topology = (Topology)request.getSession().getServletContext().getAttribute("org.apache.knox.gateway.topology");
        String whitelist = null;
        String cookieName = null;
        GatewayConfig gatewayConfig =
                (GatewayConfig) request.getServletContext().
                getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        String globalLogoutPageURL = gatewayConfig.getGlobalLogoutPageUrl();
        Collection<Service> services = topology.getServices();
        for (Object service : services) {
          Service svc = (Service)service;
          if (svc.getRole().equals("KNOXSSO")) {
            Map<String, String> params = svc.getParams();
            whitelist = params.get("knoxsso.redirect.whitelist.regex");
            // LJM TODO: get cookie name and possibly domain prefix info for use in logout
            cookieName = params.get("knoxsso.cookie.name");
            if (cookieName == null) {
                cookieName = "hadoop-jwt";
            }
          }
          break;
        }
        if (whitelist == null) {
            whitelist = WhitelistUtils.getDispatchWhitelist(request);
            if (whitelist == null) {
                whitelist = "";
            }
        }

        boolean validRedirect = Urls.isValidRedirect(originalUrl, whitelist);
        // returnToApp=1: documented API (knox_sso_logout.md); UI link below when redirect is valid.
        if (("1".equals(request.getParameter("returnToApp")))) {
          if (validRedirect) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", originalUrl);
            return;
          }
        }
        else if (("1".equals(request.getParameter("globalLogout")))) {
            /*
             * In order to account for google chrome changing default value
             * of SameSite from None to Lax we need to craft Set-Cookie
             * header to prevent issues with hadoop-jwt cookie.
             * NOTE: this would have been easier if javax.servlet.http.Cookie supported
             * SameSite param. Change this back to Cookie impl. after
             * SameSite header is supported by javax.servlet.http.Cookie.
             */
            final String clusterName = (String)request.getSession().getServletContext().getAttribute("org.apache.knox.gateway.gateway.name");
            final String domainName = Urls.getDomainName(
                    request.getRequestURL().toString(), "*");

            final String p4j_domainName = Urls.getDomainName(
                    request.getRequestURL().toString(), null);

            final String pac4jPath =  "/"+clusterName+"/knoxsso/api/v1";
            // Remove hadoop-jwt cookie
            response.addHeader("Set-Cookie", removeCookie(cookieName, domainName,"/"));

            // remove pac4j cookies
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jCsrfToken", p4j_domainName, pac4jPath));
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jCsrfTokenExpirationDate", p4j_domainName, pac4jPath));
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jPreviousCsrfToken", p4j_domainName, pac4jPath));
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jRequestedUrl", p4j_domainName, pac4jPath));
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jUserProfiles", p4j_domainName, pac4jPath));
            response.addHeader("Set-Cookie", removeCookie("pac4j.session.pac4jUserProfiles", p4j_domainName, pac4jPath+"/websso"));
            response.addHeader("Set-Cookie", removeCookie("pac4jCsrfToken", domainName, "/"));

          response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
          response.setHeader("Location", globalLogoutPageURL);
          return;
        }
    %>

    <!-- Helper function to delete cookie -->
    <%!
        public String removeCookie(String cName, String domainName, String path) {
            final StringBuilder setCookie = new StringBuilder(50);
            try {
                setCookie.append(cName).append('=');
                setCookie.append("; Path=").append(path);
                try {
                    if (domainName != null) {
                        setCookie.append("; Domain=").append(domainName);
                    }
                } catch (Exception e) {
                // do nothing
                // we are probably not going to be able to
                // remove the cookie due to this error but it
                // is not necessarily not going to work.
                }
                setCookie.append("; HttpOnly");
                setCookie.append("; Secure");
                setCookie.append("; Max-Age=").append(0);
                setCookie.append("; SameSite=None");
                return setCookie.toString();
            } catch (Exception e) {
                return "";
            }
       }
    %>
  </head>
  
  <body class="login" style="">
    <section id="signout-container">
      <div class="l-logo">
          <img src="images/knox-logo.gif" alt="Knox logo">
      </div>
        <%
            if (validRedirect) {
        %>
          <div class="signout-content l2-logo">
              <h1 class="signout-title">Session Termination</h1>
              <p class="signout-message">
                Your session has timed out or you have logged out of an application that uses SSO.
                Use <strong>Sign in again</strong> to open the login page and authenticate.
                If your Knox SSO session is still valid, choose <strong>Return to application</strong>
                to go back without entering credentials again.
              </p>
              <form action="login.html" method="get" accept-charset="utf-8">
                <fieldset>
                  <input type="hidden" name="originalUrl" value="<%= StringEscapeUtils.escapeHtml4(originalUrl) %>"/>
                  <% if (themeParam != null) { %>
                  <input type="hidden" name="theme" value="<%= StringEscapeUtils.escapeHtml4(themeParam) %>"/>
                  <% } %>
                  <button type="submit" class="btn btn-primary w-100" id="signOut" style="position: relative; text-align: center;">
                    <span>Sign in again</span>
                  </button>
                </fieldset>
              </form>
              <p class="signout-secondary">
                <a href="?returnToApp=1&amp;originalUrl=<%= StringEscapeUtils.escapeHtml4(originalUrl) %>">Return to application</a>
              </p>
        <%
            if (globalLogoutPageURL != null && !globalLogoutPageURL.isEmpty()) {
        %>
              <form method="POST" action="#" id="globalLogoutForm" class="signout-global-form">
                <p class="signout-global-message">
                If you would like to logout of the Knox SSO session, you need to do so from
                the configured SSO provider. Subsequently, authentication will be required to access
                any SSO protected resources. Note that this may or may not invalidate any previously
                established application sessions. Application sessions are subject to their application
                specific session cookies and timeouts.
                </p>
                <input type="hidden" name="globalLogout" value="1" id="globalLogoutUrl"/>
                <button type="submit" class="btn btn-primary w-100" id="signOutGlobal" style="position: relative; text-align: center;">
                  <span>Global Logout</span>
                </button>
              </form>
        <%
            }
        %>
          </div>
        <%
        }
        else {
        %>
        <div class="signout-content signout-error l2-logo">
          <h1 class="signout-title signout-error-title">ERROR</h1>
          <p class="signout-message signout-error-message">Invalid Redirect: Possible Phishing Attempt</p>
        </div>
        <%
        }
        %>
    </section>
  </body>
</html>
