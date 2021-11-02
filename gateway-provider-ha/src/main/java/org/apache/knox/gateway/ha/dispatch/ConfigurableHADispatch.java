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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A configurable HA dispatch class that has a very basic failover mechanism and
 * configurable options of ConfigurableDispatch class.
 */
public class ConfigurableHADispatch extends ConfigurableDispatch {

  protected static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";

  protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);

  private int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;

  private int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;

  private HaProvider haProvider;

  private static final Map<String, String> urlToHashLookup = new HashMap<>();
  private static final Map<String, String> hashToUrlLookup = new HashMap<>();

  private boolean loadBalancingEnabled = HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED;
  private boolean stickySessionsEnabled = HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED;
  private boolean noFallbackEnabled = HaServiceConfigConstants.DEFAULT_NO_FALLBACK_ENABLED;
  private String stickySessionCookieName = HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME;
  private List<String> disableLoadBalancingForUserAgents = Arrays.asList(HaServiceConfigConstants.DEFAULT_DISABLE_LB_USER_AGENTS);

  /**
   *  This activeURL is used to track urls when LB is turned off for some clients
   *  The problem we have with selectively turning off LB is that other clients
   *  that use LB can change the state from under the current session where LB is
   *  turned off.
   *  e.g.
   *  ODBC Connection established where LB is off. JDBC connection is established
   *  next where LB is enabled. This changes the active URL under the existing ODBC
   *  connection which will be an issue.
   *  This variable keeps track of non-LB'ed url and updated upon failover.
   */
  private AtomicReference<String> activeURL =  new AtomicReference();
  @Override
  public void init() {
    super.init();
    LOG.initializingForResourceRole(getServiceRole());
    if ( haProvider != null ) {
      HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(getServiceRole());
      maxFailoverAttempts = serviceConfig.getMaxFailoverAttempts();
      failoverSleep = serviceConfig.getFailoverSleep();
      loadBalancingEnabled = serviceConfig.isLoadBalancingEnabled();

      /* enforce dependency */
      stickySessionsEnabled = loadBalancingEnabled && serviceConfig.isStickySessionEnabled();
      noFallbackEnabled = stickySessionsEnabled && serviceConfig.isNoFallbackEnabled();
      if(stickySessionsEnabled) {
        stickySessionCookieName = serviceConfig.getStickySessionCookieName();
      }

      disableLoadBalancingForUserAgents = serviceConfig.getStickySessionDisabledUserAgents();

      setupUrlHashLookup();
    }

    /* setup the active URL for non-LB case */
    activeURL.set(haProvider.getActiveURL(getServiceRole()));

    // Suffix the cookie name by the service to make it unique
    // The cookie path is NOT unique since Knox is stripping the service name.
    stickySessionCookieName = stickySessionCookieName + '-' + getServiceRole();
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
  protected void executeRequestWrapper(HttpUriRequest outboundRequest,
          HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
          throws IOException {

      final String userAgentFromBrowser = StringUtils.isBlank(inboundRequest.getHeader("User-Agent")) ? "" : inboundRequest.getHeader("User-Agent");

      /* disable loadblancing override */
      boolean userAgentDisabled = false;

      /* disable loadbalancing in case a configured user agent is detected to disable LB */
      if(disableLoadBalancingForUserAgents.stream().anyMatch(c -> userAgentFromBrowser.contains(c))  ) {
        userAgentDisabled = true;
        LOG.disableHALoadbalancinguserAgent(userAgentFromBrowser, disableLoadBalancingForUserAgents.toString());
      }

      /* if disable LB is set don't bother setting backend from cookie */
      Optional<URI> backendURI = Optional.empty();
      if(!userAgentDisabled) {
        backendURI = setBackendfromHaCookie(outboundRequest, inboundRequest);
        if(backendURI.isPresent()) {
          ((HttpRequestBase) outboundRequest).setURI(backendURI.get());
        }
      }

      /**
       * case where loadbalancing is enabled
       * and we have a HTTP request configured not to use LB
       * use the activeURL
      */
      if(loadBalancingEnabled && userAgentDisabled) {
        try {
          ((HttpRequestBase) outboundRequest).setURI(updateHostURL(outboundRequest.getURI(), activeURL.get()));
        } catch (final URISyntaxException e) {
          LOG.errorSettingActiveUrl();
        }
      }

      executeRequest(outboundRequest, inboundRequest, outboundResponse);
      /**
       * 1. Load balance when loadbalancing is enabled and there are no overrides (disableLB)
       * 2. Loadbalance only when sticky session is enabled but cookie not detected
       *    i.e. when loadbalancing is enabled every request that does not have BACKEND cookie
       *    needs to be loadbalanced. If a request has BACKEND coookie and Loadbalance=on then
       *    there should be no loadbalancing.
       */
      if (loadBalancingEnabled && !userAgentDisabled) {
        /* check sticky session enabled */
        if(stickySessionsEnabled) {
          /* loadbalance only when sticky session enabled and no backend url cookie */
          if(!backendURI.isPresent()) {
            haProvider.makeNextActiveURLAvailable(getServiceRole());
          } else{
            /* sticky session enabled and backend url cookie is valid no need to loadbalance */
            /* do nothing */
          }
        } else {
          haProvider.makeNextActiveURLAvailable(getServiceRole());
        }
      }
  }

  @Override
  protected void outboundResponseWrapper(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest, final HttpServletResponse outboundResponse) {
      setKnoxHaCookie(outboundRequest, inboundRequest, outboundResponse);
  }

  @Override
  protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws
      IOException {
    HttpResponse inboundResponse = null;
    try {
      inboundResponse = executeOutboundRequest(outboundRequest);
      writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
    } catch ( IOException e ) {
      LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
      failoverRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
    }
  }

  private Optional<URI> setBackendfromHaCookie(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
      if (loadBalancingEnabled && stickySessionsEnabled && inboundRequest.getCookies() != null) {
          for (Cookie cookie : inboundRequest.getCookies()) {
              if (stickySessionCookieName.equals(cookie.getName())) {
                  String backendURLHash = cookie.getValue();
                  String backendURL = hashToUrlLookup.get(backendURLHash);
                  // Make sure that the url provided is actually a valid backend url
                  if (haProvider.getURLs(getServiceRole()).contains(backendURL)) {
                      try {
                        return Optional.of(updateHostURL(outboundRequest.getURI(), backendURL));
                      } catch (URISyntaxException ignore) {
                          // The cookie was invalid so we just don't set it. Knox will pick a backend automatically
                      }
                  }
              }
          }
      }
      return Optional.empty();
  }

  private void setKnoxHaCookie(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest,
          final HttpServletResponse outboundResponse) {
      if (stickySessionsEnabled) {
          List<Cookie> serviceHaCookies = Collections.emptyList();
          if(inboundRequest.getCookies() != null) {
              serviceHaCookies = Arrays
                      .stream(inboundRequest.getCookies())
                      .filter(cookie -> stickySessionCookieName.equals(cookie.getName()))
                      .collect(Collectors.toList());
          }
          /* if the inbound request has a valid hash then no need to set a different hash */
          if (serviceHaCookies != null && !serviceHaCookies.isEmpty()
                  && hashToUrlLookup.containsKey(serviceHaCookies.get(0).getValue())) {
              return;
          } else {

              /**
              * Due to concurrency issues haProvider.getActiveURL() will not return the accurate list
              * This will cause issues where original request goes to host-1 and cookie is set for host-2 - because
              * haProvider.getActiveURL() returned host-2. To prevent this from happening we need to make sure
              * we set cookie for the endpoint that was served and not rely on haProvider.getActiveURL().
              * let LBing logic take care of rotating urls.
              **/
              final List<String> urls = haProvider.getURLs(getServiceRole())
                      .stream()
                      .filter(u -> u.contains(outboundRequest.getURI().getHost()))
                      .collect(Collectors.toList());

              final String cookieValue = urlToHashLookup.get(urls.get(0));

              Cookie stickySessionCookie = new Cookie(stickySessionCookieName, cookieValue);
              stickySessionCookie.setPath(inboundRequest.getContextPath());
              stickySessionCookie.setMaxAge(-1);
              stickySessionCookie.setHttpOnly(true);
              GatewayConfig config = (GatewayConfig) inboundRequest
                      .getServletContext()
                      .getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
              if (config != null) {
                  stickySessionCookie.setSecure(config.isSSLEnabled());
              }
              outboundResponse.addCookie(stickySessionCookie);
          }
      }
  }

  protected void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
    // Check whether the session cookie is present
    Optional<Cookie> sessionCookie = Optional.empty();
    if (inboundRequest.getCookies() != null) {
        sessionCookie =
                Arrays.stream(inboundRequest.getCookies())
                      .findFirst()
                      .filter(cookie -> stickySessionCookieName.equals(cookie.getName()));
    }

    // Check for a case where no fallback is configured
    if(stickySessionsEnabled && noFallbackEnabled && sessionCookie.isPresent()) {
      LOG.noFallbackError();
      outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error, HA failover disabled");
      return;
    }
    haProvider.markFailedURL(getServiceRole(), outboundRequest.getURI().toString());
    AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
    if ( counter == null ) {
      counter = new AtomicInteger(0);
    }
    inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
    if ( counter.incrementAndGet() <= maxFailoverAttempts ) {
      setupUrlHashLookup(); // refresh the url hash after failing a url
      //null out target url so that rewriters run again
      inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);
      // Make sure to remove the cookie ha cookie from the request
      inboundRequest = new StickySessionCookieRemovedRequest(stickySessionCookieName, inboundRequest);
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
      LOG.failingOverRequest(outboundRequest.getURI().toString());

      /* in case of failover update the activeURL variable */
      activeURL.set(outboundRequest.getURI().toString());

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
  private static class StickySessionCookieRemovedRequest extends HttpServletRequestWrapper {
    private final Cookie[] cookies;

    StickySessionCookieRemovedRequest(String cookieName, HttpServletRequest request) {
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

  /**
   * A helper function that updates the schema, host and port
   * of the URI with the provided string URL and returnes a new
   * URI object
   * @param source
   * @param host
   * @return
   */
  private URI updateHostURL(final URI source, final String host) throws URISyntaxException {
    final URI newUri = new URI(host);
    final URIBuilder uriBuilder = new URIBuilder(source);
    uriBuilder.setScheme(newUri.getScheme());
    uriBuilder.setHost(newUri.getHost());
    uriBuilder.setPort(newUri.getPort());
    return uriBuilder.build();
  }
}
