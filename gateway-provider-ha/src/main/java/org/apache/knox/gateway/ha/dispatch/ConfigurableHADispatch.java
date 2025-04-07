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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.knox.gateway.util.HttpUtils.isConnectionError;

/**
 * A configurable HA dispatch class that has a very basic failover mechanism and
 * configurable options of ConfigurableDispatch class.
 */
public class ConfigurableHADispatch extends ConfigurableDispatch implements LBHaDispatch {

  protected static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";

  protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);

  protected int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;

  protected int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;

  protected HaProvider haProvider;

  protected static final List<String> nonIdempotentRequests = Arrays.asList("POST", "PATCH", "CONNECT");

  private boolean loadBalancingEnabled = HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED;
  private boolean stickySessionsEnabled = HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED;
  private boolean noFallbackEnabled = HaServiceConfigConstants.DEFAULT_NO_FALLBACK_ENABLED;
  protected boolean failoverNonIdempotentRequestEnabled = HaServiceConfigConstants.DEFAULT_FAILOVER_NON_IDEMPOTENT;
  private String stickySessionCookieName = HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME;
  private List<String> disableLoadBalancingForUserAgents = Arrays.asList(HaServiceConfigConstants.DEFAULT_DISABLE_LB_USER_AGENTS);

  /**
   *  This activeURL is used to track urls when LB is turned off for some clients.
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
      failoverNonIdempotentRequestEnabled = serviceConfig.isFailoverNonIdempotentRequestEnabled();
      initializeLBHaDispatch(serviceConfig);
      noFallbackEnabled = stickySessionsEnabled && serviceConfig.isNoFallbackEnabled();
    }
  }

  @Override
  public HaProvider getHaProvider() {
    return haProvider;
  }

  @Configure
  public void setHaProvider(HaProvider haProvider) {
    this.haProvider = haProvider;
  }

  @Override
  public boolean isStickySessionEnabled() {
      return stickySessionsEnabled;
  }

  @Override
  public void setStickySessionsEnabled(boolean enabled) {
    this.stickySessionsEnabled = enabled;
  }

  @Override
  public String getStickySessionCookieName() {
      return stickySessionCookieName;
  }

  @Override
  public void setStickySessionCookieName(String stickySessionCookieName) {
    this.stickySessionCookieName = stickySessionCookieName;
  }

  @Override
  public boolean isLoadBalancingEnabled() {
      return loadBalancingEnabled;
  }

  @Override
  public void setLoadBalancingEnabled(boolean enabled) {
    this.loadBalancingEnabled = enabled;
  }

  @Override
  public List<String> getDisableLoadBalancingForUserAgents() {
      return disableLoadBalancingForUserAgents;
  }

  @Override
  public void setDisableLoadBalancingForUserAgents(List<String> disableLoadBalancingForUserAgents) {
    this.disableLoadBalancingForUserAgents = disableLoadBalancingForUserAgents;
  }

  @Override
  public AtomicReference<String> getActiveURL() {
      return activeURL;
  }

  @Override
  public void setActiveURL(String url) {
    activeURL.set(url);
  }

  @Override
  protected void executeRequestWrapper(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
      boolean userAgentDisabled = isUserAgentDisabled(inboundRequest);
      Optional<URI> backendURI = setBackendUri(outboundRequest, inboundRequest, userAgentDisabled);
      executeRequest(outboundRequest, inboundRequest, outboundResponse);
      shiftActiveURL(userAgentDisabled, backendURI);
  }

  @Override
  protected void outboundResponseWrapper(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest, final HttpServletResponse outboundResponse) {
      GatewayConfig config = (GatewayConfig) inboundRequest
              .getServletContext()
              .getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      boolean sslEnabled = config != null && config.isSSLEnabled();

      setKnoxHaCookie(outboundRequest, inboundRequest, outboundResponse, sslEnabled);
  }

  @Override
  protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws
      IOException {
    HttpResponse inboundResponse = null;
    try {
      inboundResponse = executeOutboundRequest(outboundRequest);
      writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
    } catch ( IOException e ) {
      /* if non-idempotent requests are not allowed to failover, unless it's a connection error */
      if(!isConnectionError(e.getCause()) && isNonIdempotentAndNonIdempotentFailoverDisabled(outboundRequest)) {
        LOG.cannotFailoverNonIdempotentRequest(outboundRequest.getMethod(), e.getCause());
        /* mark endpoint as failed */
        markEndpointFailed(outboundRequest, inboundRequest);
        throw e;
      } else {
        LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
        failoverRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
      }
    }
  }

  private boolean isNonIdempotentAndNonIdempotentFailoverDisabled(HttpUriRequest outboundRequest) {
    return !failoverNonIdempotentRequestEnabled && nonIdempotentRequests.stream().anyMatch(outboundRequest.getMethod()::equalsIgnoreCase);
  }

  protected void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
    // Check whether the session cookie is present
    Optional<Cookie> sessionCookie = Optional.empty();
    if (inboundRequest.getCookies() != null) {
        sessionCookie =
                Arrays.stream(inboundRequest.getCookies())
                      .filter(cookie -> stickySessionCookieName.equals(cookie.getName()))
                      .findFirst();
    }

    // Check for a case where no fallback is configured
    if(stickySessionsEnabled && noFallbackEnabled && sessionCookie.isPresent()) {
      LOG.noFallbackError();
      outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error, HA failover disabled");
      return;
    }
    /* mark endpoint as failed */
    final AtomicInteger counter = markEndpointFailed(outboundRequest, inboundRequest);
    inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
    if ( counter.get() <= maxFailoverAttempts ) {
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

  /**
   * A helper method that marks an endpoint failed.
   * Changes HA Provider state.
   * Changes ActiveUrl state.
   * Changes for inbound urls should be handled by calling functions.
   * @param outboundRequest
   * @param inboundRequest
   * @return current failover counter
   */
  private synchronized AtomicInteger markEndpointFailed(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest) {
    haProvider.markFailedURL(getServiceRole(), outboundRequest.getURI().toString());
    AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
    if ( counter == null ) {
      counter = new AtomicInteger(0);
    }

    if ( counter.incrementAndGet() <= maxFailoverAttempts ) {
      setupUrlHashLookup(); // refresh the url hash after failing a url
      /* in case of failover update the activeURL variable */
      activeURL.set(outboundRequest.getURI().toString());
    }
    return counter;
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
}
