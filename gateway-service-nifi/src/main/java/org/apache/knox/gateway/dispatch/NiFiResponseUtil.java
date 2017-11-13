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
package org.apache.knox.gateway.dispatch;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

class NiFiResponseUtil {

  static void modifyOutboundResponse(HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse) throws IOException {
    // Only want to rewrite the Location header on a HTTP 302
    if (inboundResponse.getStatusLine().getStatusCode() == HttpServletResponse.SC_FOUND) {
      Header originalLocationHeader = inboundResponse.getFirstHeader("Location");
      if (originalLocationHeader != null) {
        String originalLocation = originalLocationHeader.getValue();
        URIBuilder originalLocationUriBuilder;
        try {
          originalLocationUriBuilder = new URIBuilder(originalLocation);
        } catch (URISyntaxException e) {
          throw new RuntimeException("Unable to parse URI from Location header", e);
        }
        URIBuilder inboundRequestUriBuilder = null;
        try {
          inboundRequestUriBuilder = new URIBuilder(inboundRequest.getRequestURI());
        } catch (URISyntaxException e) {
          throw new RuntimeException("Unable to parse the inbound request URI", e);
        }
        /*
         * if the path specified in the Location header fron the inbound response contains the inbound request's URI's path,
         * then it's going to the same web context, and the Location header should be updated based on the X_FORWARDED_* headers.
         */
        String inboundRequestUriPath = inboundRequestUriBuilder.getPath();
        String originalLocationUriPath = originalLocationUriBuilder.getPath();
        if (originalLocationUriPath.contains(inboundRequestUriPath)) {
          // check for trailing slash of Location header if it exists and preserve it
          final String trailingSlash = originalLocationUriPath.endsWith("/") ? "/" : "";
          // retain query params
          final List<NameValuePair> queryParams = originalLocationUriBuilder.getQueryParams();

          // check for proxy settings
          final String scheme = inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_PROTO);
          final String host = inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_HOST);
          final String port = inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_PORT);

          final String baseContextPath = inboundRequest.getHeader(NiFiHeaders.X_FORWARDED_CONTEXT);
          final String pathInfo = inboundRequest.getPathInfo();

          try {
            final URI newLocation = new URIBuilder().setScheme(scheme).setHost(host).setPort((StringUtils.isNumeric(port) ? Integer.parseInt(port) : -1)).setPath(
                baseContextPath + pathInfo + trailingSlash).setParameters(queryParams).build();
            outboundResponse.setHeader("Location", newLocation.toString());
          } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to rewrite Location header in response", e);
          }
        }
      } else {
        throw new RuntimeException("Received HTTP 302, but response is missing Location header");
      }
    }
  }
}

