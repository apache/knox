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
package org.apache.knox.gateway.ha.dispatch;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.dispatch.DefaultDispatch;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.ha.dispatch.i18n.HaDispatchMessages;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Default HA dispatch class that has a very basic failover mechanism
 */
public class DefaultHaDispatch extends DefaultDispatch {

  protected static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";

  protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);

  private static final Map<String, String> urlToHashLookup = new HashMap<>();
  private static final Map<String, String> hashToUrlLookup = new HashMap<>();

  private int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;
  private int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;

  private boolean cookieHaEnabled = HaServiceConfigConstants.DEFAULT_COOKIE_HA_ENABLED;
  private String cookieHaCookieName = HaServiceConfigConstants.DEFAULT_COOKIE_HA_COOKIE_NAME;

  private HaProvider haProvider;

  @Override
  public void init() {
    super.init();
    LOG.initializingForResourceRole(getServiceRole());
    if ( haProvider != null ) {
      HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(getServiceRole());
      maxFailoverAttempts = serviceConfig.getMaxFailoverAttempts();
      failoverSleep = serviceConfig.getFailoverSleep();
      cookieHaEnabled = serviceConfig.isCookieHaEnabled();
      cookieHaCookieName = serviceConfig.getCookieHaCookieName();
      setupUrlHashLookup();
    }

    // Suffix the cookie name by the service to make it unique
    // The cookie path is NOT unique since Knox is stripping the service name.
    cookieHaCookieName = cookieHaCookieName + '-' + getServiceRole();
  }

  private void setupUrlHashLookup() {
    for (String url : haProvider.getURLs(getServiceRole())) {
      String urlHash = hash(url);
      urlToHashLookup.put(url, urlHash);
      hashToUrlLookup.put(urlHash, url);
    }
  }

  public HaProvider getHaProvider() {
    return haProvider;
  }

  @Configure
  public void setHaProvider(HaProvider haProvider) {
    this.haProvider = haProvider;
  }

  @Override
  protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
    HttpResponse inboundResponse = null;
    try {
      setBackendfromHaCookie(outboundRequest, inboundRequest);
      inboundResponse = executeOutboundRequest(outboundRequest);
      setKnoxHaCookie(inboundRequest, outboundResponse);
      writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
      if (cookieHaEnabled) {
        haProvider.makeNextActiveURLAvailable(getServiceRole());
      }
    } catch ( IOException e ) {
      LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
      failoverRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
    }
  }

  private void setBackendfromHaCookie(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
    if (cookieHaEnabled && inboundRequest.getCookies() != null) {
      for (Cookie cookie : inboundRequest.getCookies()) {
        if (cookieHaCookieName.equals(cookie.getName())) {
          String backendURLHash = cookie.getValue();
          String backendURL = hashToUrlLookup.get(backendURLHash);
          // Make sure that the url provided is actually a valid backend url
          if (haProvider.getURLs(getServiceRole()).contains(backendURL)) {
            try {
              URI cookieUri = new URI(backendURL);
              URIBuilder uriBuilder = new URIBuilder(outboundRequest.getURI());
              uriBuilder.setScheme(cookieUri.getScheme());
              uriBuilder.setHost(cookieUri.getHost());
              uriBuilder.setPort(cookieUri.getPort());
              URI uri = uriBuilder.build();
              ((HttpRequestBase) outboundRequest).setURI(uri);
            } catch (URISyntaxException ignore) {
              // The cookie was invalid so we just don't set it. Knox will pick a backend automatically
            }
          }
        }
      }
    }
  }

  private void setKnoxHaCookie(HttpServletRequest inboundRequest,
      HttpServletResponse outboundResponse) {
    if (cookieHaEnabled) {
      List<Cookie> serviceHaCookies = Collections.emptyList();
      if(inboundRequest.getCookies() != null) {
        serviceHaCookies = Arrays
            .stream(inboundRequest.getCookies())
            .filter(cookie -> cookieHaCookieName.equals(cookie.getName()))
            .collect(Collectors.toList());
      }
      /* if the inbound request has a valid hash then no need to set a different hash */
      if (serviceHaCookies != null && !serviceHaCookies.isEmpty()
          && hashToUrlLookup.containsKey(serviceHaCookies.get(0).getValue())) {
        return;
      } else {
        String url = haProvider.getActiveURL(getServiceRole());
        String cookieValue = urlToHashLookup.get(url);
        Cookie cookieHaCookie = new Cookie(cookieHaCookieName, cookieValue);
        cookieHaCookie.setPath(inboundRequest.getContextPath());
        cookieHaCookie.setMaxAge(-1);
        cookieHaCookie.setHttpOnly(true);
        GatewayConfig config = (GatewayConfig) inboundRequest
            .getServletContext()
            .getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
        if (config != null) {
          cookieHaCookie.setSecure(config.isSSLEnabled());
        }
        outboundResponse.addCookie(cookieHaCookie);
      }
    }
  }

  protected void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
    LOG.failingOverRequest(outboundRequest.getURI().toString());
    AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
    if ( counter == null ) {
      counter = new AtomicInteger(0);
    }
    inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
    if ( counter.incrementAndGet() <= maxFailoverAttempts ) {
      haProvider.markFailedURL(getServiceRole(), outboundRequest.getURI().toString());
      setupUrlHashLookup(); // refresh the url hash after failing a url
      //null out target url so that rewriters run again
      inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);
      // Make sure to remove the cookie ha cookie from the request
      inboundRequest = new CookieHaCookieRemovedRequest(cookieHaCookieName, inboundRequest);
      URI uri = getDispatchUrl(inboundRequest);
      ((HttpRequestBase) outboundRequest).setURI(uri);
      if ( failoverSleep > 0 ) {
        try {
          Thread.sleep(failoverSleep);
        } catch ( InterruptedException e ) {
          LOG.failoverSleepFailed(getServiceRole(), e);
          Thread.currentThread().interrupt();
        }
      }
      executeRequest(outboundRequest, inboundRequest, outboundResponse);
    } else {
      LOG.maxFailoverAttemptsReached(maxFailoverAttempts, getServiceRole());
      if ( inboundResponse != null ) {
        writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
      } else {
        throw new IOException(exception);
      }
    }
  }

  private String hash(String url) {
    return DigestUtils.sha256Hex(url);
  }

  /**
   * Strips out the cookies by the cookie name provided
   */
  private static class CookieHaCookieRemovedRequest extends HttpServletRequestWrapper {
    private final Cookie[] cookies;

    CookieHaCookieRemovedRequest(String cookieName, HttpServletRequest request) {
      super(request);
      this.cookies = filterCookies(cookieName, request.getCookies());
    }

    private Cookie[] filterCookies(String cookieName, Cookie[] cookies) {
      if (super.getCookies() == null) {
        return null;
      }
      List<Cookie> cookiesInternal = new ArrayList<>();
      for (Cookie cookie : cookies) {
        if (!cookieName.equals(cookie.getName())) {
          cookiesInternal.add(cookie);
        }
      }
      return cookiesInternal.toArray(new Cookie[0]);
    }

    @Override
    public Cookie[] getCookies() {
      return cookies;
    }
  }
}
