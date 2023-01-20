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
<%@ page import="java.net.MalformedURLException" %>
<%@ page import="org.apache.knox.gateway.topology.Topology" %>
<%@ page import="org.apache.knox.gateway.topology.Service" %>
<%@ page import="org.apache.knox.gateway.util.RegExUtils" %>
<%@ page import="org.apache.knox.gateway.util.Urls" %>
<%@ page import="org.apache.knox.gateway.util.WhitelistUtils" %>

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

        <link rel="shortcut icon" href="images/favicon.ico">
        <link href="styles/bootstrap.min.css" media="all" rel="stylesheet" type="text/css" id="bootstrap-css">
        <link href="styles/knox.css" media="all" rel="stylesheet" type="text/css" >

        <script src="libs/bower/jquery/js/jquery-3.5.1.min.js" ></script>

        <script type="text/javascript" src="js/knoxauth.js"></script>
    <%
        boolean validRedirect = true;
        String originalUrl = request.getParameter("originalUrl");
        if (originalUrl == null) {
            originalUrl = "";
        }
        try {
          if (Urls.containsUserInfo(originalUrl)) {
            validRedirect = false;
          }
        }
        catch (MalformedURLException ex) {
          // if not a well formed URL then not a valid redirect
          validRedirect = false;
        }
        if (validRedirect) {
	      Topology topology = (Topology)request.getSession().getServletContext().getAttribute("org.apache.knox.gateway.topology");
          String whitelist = null;
          Collection services = topology.getServices();
          for (Object service : services) {
            Service svc = (Service)service;
            if (svc.getRole().equals("KNOXSSO")) {
              Map<String, String> params = svc.getParams();
              whitelist = params.get("knoxsso.redirect.whitelist.regex");
            }
          }
          if (whitelist == null) {
            whitelist = WhitelistUtils.getDispatchWhitelist(request);
            if (whitelist == null) {
              whitelist = "";
            }
          }
          validRedirect = RegExUtils.checkWhitelist(whitelist, originalUrl);
        }
        if (validRedirect) {
    %>
    <script>
    document.addEventListener("load", redirectOnLoad());

    function redirectOnLoad() {
      var originalUrl = "<%= originalUrl %>";
      if (originalUrl != null) {
        redirect(originalUrl);
      }
    }
    </script>
    <%
    }
    %>
  </head>
 
  <body>
        <section id="signin-container" style="margin-top: 80px;">
        <%
            if (validRedirect) {
        %>
          <div style="background: gray;text-color: white;text-align:center;">
          <h1 style="color: white;">Loading...</h1>
          <div style="background: white;" class="l-logo">
                <img src="images/loading.gif" alt="Knox logo" style="text-align:center;width: 2%; height: 2%">
            </div>
              <p style="color: white;display: block">Loading should complete in few a seconds. If not, click <a href="<%= originalUrl %>">here</a></p>
        <%
        } else {
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
