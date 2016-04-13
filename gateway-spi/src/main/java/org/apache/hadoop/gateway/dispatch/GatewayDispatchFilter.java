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
package org.apache.hadoop.gateway.dispatch;

import org.apache.hadoop.gateway.SpiGatewayMessages;
import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.apache.hadoop.gateway.config.ConfigurationInjectorBuilder.configuration;

public class GatewayDispatchFilter extends AbstractGatewayFilter {

  private static Map<String, Adapter> METHOD_ADAPTERS = createMethodAdapters();

  protected static SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);

  private Dispatch dispatch;

  private HttpClient httpClient;

  private static Map<String, Adapter> createMethodAdapters() {
    Map<String, Adapter> map = new HashMap<>();
    map.put("GET", new GetAdapter());
    map.put("POST", new PostAdapter());
    map.put("PUT", new PutAdapter());
    map.put("DELETE", new DeleteAdapter());
    map.put("OPTIONS", new OptionsAdapter());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    if (dispatch == null) {
      String dispatchImpl = filterConfig.getInitParameter("dispatch-impl");
      dispatch = newInstanceFromName(dispatchImpl);
    }
    configuration().target(dispatch).source(filterConfig).inject();
    HttpClientFactory httpClientFactory;
    String httpClientFactoryClass = filterConfig.getInitParameter("httpClientFactory");
    if (httpClientFactoryClass != null) {
      httpClientFactory = newInstanceFromName(httpClientFactoryClass);
    } else {
      httpClientFactory = new DefaultHttpClientFactory();
    }
    httpClient = httpClientFactory.createHttpClient(filterConfig);
    dispatch.setHttpClient(httpClient);
    dispatch.init();
  }

  @Override
  public void destroy() {
    dispatch.destroy();
    try {
      if (httpClient instanceof  CloseableHttpClient) {
        ((CloseableHttpClient) httpClient).close();
      }
    } catch ( IOException e ) {
      LOG.errorClosingHttpClient(e);
    }
  }

  public Dispatch getDispatch() {
    return dispatch;
  }

  public void setDispatch(Dispatch dispatch) {
    this.dispatch = dispatch;
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    String method = request.getMethod().toUpperCase();
    Adapter adapter = METHOD_ADAPTERS.get(method);
    if ( adapter != null ) {
      try {
        adapter.doMethod(dispatch, request, response);
      } catch ( URISyntaxException e ) {
        throw new ServletException(e);
      }
    } else {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  private interface Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException;
  }

  private static class GetAdapter implements Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doGet( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class PostAdapter implements Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPost( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class PutAdapter implements Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPut( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class DeleteAdapter implements Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doDelete( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class OptionsAdapter implements Adapter {
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doOptions( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private <T> T newInstanceFromName(String dispatchImpl) throws ServletException {
    try {
      Class<T> clazz = loadClass(dispatchImpl);
      return clazz.newInstance();
    } catch ( Exception e ) {
      throw new ServletException(e);
    }
  }

  private <T> Class<T> loadClass(String className) throws ClassNotFoundException {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if ( loader == null ) {
        loader = this.getClass().getClassLoader();
      }
      return (Class<T>) loader.loadClass(className);
  }
}
