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
package org.apache.knox.gateway.filter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.knox.gateway.audit.api.*;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;

import javax.security.auth.Subject;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RemoteAuthFilter implements Filter {

  private static final String CONFIG_REMOTE_AUTH_URL = "remote.auth.url";
  private static final String CONFIG_INCLUDE_HEADERS = "remote.auth.include.headers";
  private static final String CONFIG_CACHE_KEY_HEADER = "remote.auth.cache.key";
  private static final String CONFIG_EXPIRE_AFTER = "remote.auth.expire.after";
  private static final String DEFAULT_CACHE_KEY_HEADER = "Authorization";
  private static final String CONFIG_USER_HEADER = "remote.auth.user.header";
  private static final String CONFIG_GROUP_HEADER = "remote.auth.group.header";
  private static final String CONFIG_TRUSTSTORE_LOCATION = "remote.auth.truststore.location";
  public static final String CONFIG_TRUSTSTORE_PWD = "remote.auth.truststore.password";

  private String remoteAuthUrl;
  private List<String> includeHeaders;
  private String cacheKeyHeader;
  private String userHeader;
  private String groupHeader;
  /*
  For Testing
   */
  HttpURLConnection httpURLConnection;

  Cache<String, Subject> authenticationCache;

  private static final AuditService auditService = AuditServiceFactory.getAuditService();
  private static final Auditor auditor = auditService.getAuditor(
          AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    remoteAuthUrl = filterConfig.getInitParameter(CONFIG_REMOTE_AUTH_URL);
    if (remoteAuthUrl == null || remoteAuthUrl.isEmpty()) {
      throw new ServletException(CONFIG_REMOTE_AUTH_URL + " is a missing required param.");
    }
    includeHeaders = Arrays.asList(filterConfig.getInitParameter(CONFIG_INCLUDE_HEADERS).split(","));
    cacheKeyHeader = filterConfig.getInitParameter(CONFIG_CACHE_KEY_HEADER) != null ? filterConfig
            .getInitParameter(CONFIG_CACHE_KEY_HEADER) : DEFAULT_CACHE_KEY_HEADER;
    String cachetime = filterConfig.getInitParameter(CONFIG_EXPIRE_AFTER);
    if (cachetime != null) {
      int expireAfterMinutes = Integer.parseInt(cachetime);
      authenticationCache = CacheBuilder.newBuilder()
              .expireAfterWrite(expireAfterMinutes, TimeUnit.MINUTES)
              .build();
    }

    userHeader = filterConfig.getInitParameter(CONFIG_USER_HEADER);
    if (userHeader == null || userHeader.isEmpty()) {
      throw new ServletException(CONFIG_USER_HEADER + " is a missing required param.");
    }

    groupHeader = filterConfig.getInitParameter(CONFIG_GROUP_HEADER);
    if (groupHeader == null || groupHeader.isEmpty()) {
      throw new ServletException(CONFIG_GROUP_HEADER + " is a missing required param.");
    }

    System.setProperty("javax.net.ssl.trustStore", filterConfig.getInitParameter(CONFIG_TRUSTSTORE_LOCATION));
    System.setProperty("javax.net.ssl.trustStorePassword", filterConfig.getInitParameter(CONFIG_TRUSTSTORE_PWD));
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String cacheKey = httpRequest.getHeader(cacheKeyHeader);
    Subject cachedSubject = authenticationCache.getIfPresent(cacheKey);

    if (cachedSubject != null) {
      continueWithEstablishedSecurityContext(cachedSubject, httpRequest, httpResponse, filterChain);
      return;
    }

    try {
      HttpURLConnection connection = getHttpURLConnection();
      for (String header : includeHeaders) {
        String headerValue = httpRequest.getHeader(header);
        if (headerValue != null) {
          connection.addRequestProperty(header, headerValue);
        }
      }

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        String principalName = connection.getHeaderField(userHeader);
        String groupNames = connection.getHeaderField(groupHeader);
        Subject subject = new Subject();
        subject.getPrincipals().add(new PrimaryPrincipal(principalName));
        // Add groups to the principal if available
        if(groupNames != null && !groupNames.isEmpty()) {
          Arrays.stream(groupNames.split(",")).forEach(groupName -> subject.getPrincipals()
                  .add(new GroupPrincipal(groupName)));
        }

        authenticationCache.put(cacheKey, subject);

        AuditContext context = auditService.getContext();
        if (context != null) {
          context.setUsername( principalName );
          auditService.attachContext(context);
          String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
          auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS );
        }

        continueWithEstablishedSecurityContext(subject, httpRequest, httpResponse, filterChain);
      } else {
        httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
      }
    } catch (Exception e) {
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing authentication request");
    }
  }

  private HttpURLConnection getHttpURLConnection() throws IOException {
    HttpURLConnection connection;
    if (httpURLConnection == null) {
      URL url = new URL(remoteAuthUrl);
      connection = (HttpURLConnection) url.openConnection();
    } else {
      connection = httpURLConnection;
    }
    return connection;
  }

  private void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
    try {
      Subject.doAs(
              subject,
              new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws Exception {
                  chain.doFilter(request, response);
                  return null;
                }
              }
      );
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
  }

  @Override
  public void destroy() {
  }
}