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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

/**
 * HttpServletRequest
 *
 * In section SRV.3.1.1 of Servlet spec, it has been stated that any access to request parameters
 * of an "x-www-form-urlencoded" request (e.g. using HttpServletRequest#getParameter())
 * can trigger the early consumption of the request InputStream.
 *
 * For example:
 *    $ curl -u admin:admin-password -H "X-XSRF-Header: whatever" -k -X POST -d "a=1&b=2" https://localhost:8443/gateway/sandbox/hive?doAs=KNOX
 *
 * The getParameter("a") will parse (and CONSUME) request body "a=1&b=2" and return "1".
 * The request body is treated as it was a query string, even though it is not part of the URL.
 * The parameters from the body are mixed together with the query string parameters of the URL.
 *
 * The problem is that various authentication filters (such as HadoopAuthFilter) check if there is a doAs parameter in request.
 * This will consume the input stream and the dispatch will forward an empty body to the service.
 *
 * To avoid this problem all "x-www-form-urlencoded" requests are wrapped into UrlEncodedFormRequest.
 *
 * This class ignores the request body when accessing the parameters (since KNOX as a proxy doesn't care about the payload either),
 * and it only cares about the query string.
 *
 */
public class UrlEncodedFormRequest extends HttpServletRequestWrapper {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  private final MultiMap<String> queryParams;

  public UrlEncodedFormRequest(HttpServletRequest request) throws IOException {
    super(request);
    LOG.wrappingRequestToUrlEncodedFormRequest(request.getRequestURI());
    this.queryParams = parseQueryString(request.getQueryString());
  }

  public static boolean isUrlEncodedForm(ServletRequest request) {
    String contentType = request.getContentType();
    return contentType != null && contentType.startsWith("application/x-www-form-urlencoded");
  }

  private MultiMap<String> parseQueryString(String queryString) {
    MultiMap<String> params = new MultiMap<>();
    if (queryString != null) {
      UrlEncoded.decodeTo(queryString, params, getCharacterEncoding());
    }
    return params;
  }

  @Override
  public String getParameter(String name) {
    return queryParams.getValue(name, 0);
  }

  @Override
  public String[] getParameterValues(String name) {
    return getParameterMap().get(name);
  }

  @Override
  public Map<String, String[]> getParameterMap() {
    return queryParams.toStringArrayMap();
  }

  @Override
  public Enumeration<String> getParameterNames() {
    final Iterator<String> iterator = queryParams.keySet().iterator();
    return new Enumeration<String>() {
      @Override
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }

      @Override
      public String nextElement() {
        return iterator.next();
      }
    };
  }
}
