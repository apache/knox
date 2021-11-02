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
<%@ page import="org.apache.knox.gateway.util.RegExUtils" %>
<%@ page import="org.apache.knox.gateway.util.WhitelistUtils" %>
<%@ page import="org.apache.knox.gateway.config.GatewayConfig" %>
<%@ page import="java.net.MalformedURLException" %>
<%@ page import="org.apache.knox.gateway.util.Urls" %>

<!DOCTYPE html>
<!--[if lt IE 7]><html class="no-js lt-ie9 lt-ie8 lt-ie7"><![endif]-->
<!--[if IE 7]><html class="no-js lt-ie9 lt-ie8"><![endif]-->
<!--[if IE 8]><html class="no-js lt-ie9"><![endif]-->
<!--[if gt IE 8]><!-->
<html class="no-js">
    <!--<![endif]-->
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
        <meta name="description" content="">
        <meta name="viewport" content="width=device-width">
        <meta http-equiv="Content-Type" content="text/html;charset=utf-8"/>
        <meta http-equiv="Cache-Control" content="no-cache, no-store, must-revalidate">
        <meta http-equiv="Pragma" content="no-cache">
        <meta http-equiv="Expires" content="0">

        <link rel="shortcut icon" href="images/favicon.ico">
        <link href="styles/bootstrap.min.css" media="all" rel="stylesheet" type="text/css" id="bootstrap-css">
        <link href="styles/knox.css" media="all" rel="stylesheet" type="text/css" >

        <script src="libs/bower/jquery/js/jquery-3.5.1.min.js" ></script>

        <script type="text/javascript" src="js/knoxauth.js"></script>
    <%
        String originalUrl = request.getParameter("originalUrl");
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

        boolean validRedirect = false;
        String origUrl = request.getParameter("originalUrl");
        String del = "?";
        if (origUrl != null && origUrl.contains("?")) {
          del = "&";
        }
        if (origUrl != null) {
          validRedirect = RegExUtils.checkWhitelist(whitelist, origUrl);
        }
        if (("1".equals(request.getParameter("returnToApp")))) {
          if (validRedirect) {
          	response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
          	response.setHeader("Location",originalUrl + del + "refresh=1");
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
    <section id="signout-container" style="margin-top: 4.5px;">
      <div class="l-logo">
          <img src="images/knox-logo.gif" alt="Knox logo">
      </div>
        <%
            if (validRedirect) {
        %>
          <h1 style="color: gray;">Session Termination</h1>
          <div style="background: dark-gray;" class="l2-logo">
              <p style="color: white;display: block">
                Your session has timed out or you have attempted to logout of an application
                that is participating in SSO. You may establish a new session by returning to
                the application. If your previously established SSO session is still valid then
                you will likely be automatically logged into your application. Otherwise, you
                will be required to login again.
                <a href="?returnToApp=1&originalUrl=<%= originalUrl %>" >Return to Application</a>
              </p>
        <%
            if (globalLogoutPageURL != null && !globalLogoutPageURL.isEmpty()) {
        %>
              <p style="color: white;display: block">
                If you would like to logout of the Knox SSO session, you need to do so from
                the configured SSO provider. Subsequently, authentication will be required to access
                any SSO protected resources. Note that this may or may not invalidate any previously
                established application sessions. Application sessions are subject to their application
                specific session cookies and timeouts.
                <a href="<%= request.getRequestURI() %>?globalLogout=1" >Global Logout</a>
              </p>
          </div>
        <%
            }
        } 
        else {
        %>
        <div style="background: gray;text-color: white;text-align:center;">
          <h1 style="color: red;">ERROR</h1>
          <div style="background: white;" class="l-logo">
          </div>
          <p style="color: white;display: block">Invalid Redirect: Possible Phishing Attempt</p>
        <%
        }
        %>
        </div>
    </section>
  </body>
</html>
