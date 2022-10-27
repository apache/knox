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

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.config.ConfigurationInjectorBuilder;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.RegExUtils;
import org.apache.knox.gateway.util.WhitelistUtils;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GatewayDispatchFilter extends AbstractGatewayFilter {

  private static final Map<String, Adapter> METHOD_ADAPTERS = createMethodAdapters();

  protected static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);

  private final Object lock = new Object();

  private String whitelist;

  private Dispatch dispatch;

  private HttpClient httpClient;

  private static Map<String, Adapter> createMethodAdapters() {
    Map<String, Adapter> map = new HashMap<>();
    map.put("GET", new GetAdapter());
    map.put("POST", new PostAdapter());
    map.put("PUT", new PutAdapter());
    map.put("PATCH", new PatchAdapter());
    map.put("DELETE", new DeleteAdapter());
    map.put("OPTIONS", new OptionsAdapter());
    map.put("HEAD", new HeadAdapter());
    return Collections.unmodifiableMap(map);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    super.init(filterConfig);
    synchronized(lock) {
      if (dispatch == null) {
        String dispatchImpl = filterConfig.getInitParameter("dispatch-impl");
        dispatch = newInstanceFromName(dispatchImpl);
      }
      ConfigurationInjectorBuilder.configuration().target(dispatch).source(filterConfig).inject();
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
  }

  @Override
  public void destroy() {
    synchronized(lock) {
      dispatch.destroy();
      try {
        if (httpClient instanceof  CloseableHttpClient) {
          ((CloseableHttpClient) httpClient).close();
        }
      } catch ( IOException e ) {
        LOG.errorClosingHttpClient(e);
      }
    }
  }

  public Dispatch getDispatch() {
    synchronized(lock) {
      return dispatch;
    }
  }

  public void setDispatch(Dispatch dispatch) {
    synchronized(lock) {
      this.dispatch = dispatch;
    }
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    String method = request.getMethod().toUpperCase(Locale.ROOT);
    Adapter adapter = METHOD_ADAPTERS.get(method);
    if (adapter != null) {
      if (isDispatchAllowed(request)) {
        try {
          adapter.doMethod(getDispatch(), request, response);
        } catch (URISyntaxException e) {
          throw new ServletException(e);
        }
      } else {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
  }

  private boolean isDispatchAllowed(HttpServletRequest request) {
    boolean isAllowed = true;

      // Initialize the white list if it has not yet been initialized
      if (whitelist == null) {
        whitelist = WhitelistUtils.getDispatchWhitelist(request);
      }

      if (whitelist != null) {
        String requestURI = request.getRequestURI();

        String decodedURL = requestURI;
        try {
          decodedURL = URLDecoder.decode(requestURI, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
          //
        }
        String baseUrl;
        try {
          URL url = new URL(decodedURL);
          baseUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), "").toString();
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }

        isAllowed = RegExUtils.checkWhitelist(whitelist, baseUrl);

        if (!isAllowed) {
          LOG.dispatchDisallowed(requestURI);
        }
    }

    return isAllowed;
  }

  private interface Adapter {
    void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException;
  }

  private static class GetAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doGet( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class PostAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPost( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class PutAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPut( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class PatchAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doPatch( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class DeleteAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doDelete( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class OptionsAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doOptions( dispatch.getDispatchUrl(request), request, response);
    }
  }

  private static class HeadAdapter implements Adapter {
    @Override
    public void doMethod(Dispatch dispatch, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException, URISyntaxException {
      dispatch.doHead( dispatch.getDispatchUrl(request), request, response);
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
