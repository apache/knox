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
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;
import org.apache.knox.gateway.ha.dispatch.i18n.HaDispatchMessages;
import org.apache.knox.gateway.ha.config.CommonHaConfigurations;
import org.apache.knox.gateway.ha.config.HaConfigurations;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.knox.gateway.util.HttpUtils.isConnectionError;

/**
 * A configurable HA dispatch class that has a very basic failover mechanism and
 * configurable options of ConfigurableDispatch class.
 */
public class ConfigurableHADispatch extends ConfigurableDispatch implements CommonHaDispatch {

  protected static final HaDispatchMessages LOG = MessagesFactory.get(HaDispatchMessages.class);
  private final HaConfigurations haConfigurations = new CommonHaConfigurations();

  /**
   * This activeURL is used to track urls when LB is turned off for some clients.
   * The problem we have with selectively turning off LB is that other clients
   * that use LB can change the state from under the current session where LB is
   * turned off.
   * e.g.
   * ODBC Connection established where LB is off. JDBC connection is established
   * next where LB is enabled. This changes the active URL under the existing ODBC
   * connection which will be an issue.
   * This variable keeps track of non-LB'ed url and updated upon failover.
   */
  private final AtomicReference<String> activeURL = new AtomicReference<>();

  @Override
  public void init() {
    super.init();
    LOG.initializingForResourceRole(getServiceRole());
    if (haConfigurations.getHaProvider() != null) {
      initializeCommonHaDispatch(haConfigurations.getHaProvider().getHaDescriptor().getServiceConfig(getServiceRole()));
    }
  }

  @Override
  public HaConfigurations getHaConfigurations() {
    return haConfigurations;
  }

  @Configure
  public void setHaProvider(HaProvider haProvider) {
    getHaConfigurations().setHaProvider(haProvider);
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

  protected void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
    if (disabledFailoverHandled(inboundRequest, outboundResponse)) {
      return;
    }

    /* mark endpoint as failed */
    final AtomicInteger counter = markEndpointFailed(outboundRequest, inboundRequest);
    inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
    if ( counter.get() <= haConfigurations.getMaxFailoverAttempts() ) {
      inboundRequest = prepareForFailover(outboundRequest, inboundRequest);
      executeRequest(outboundRequest, inboundRequest, outboundResponse);
    } else {
      LOG.maxFailoverAttemptsReached(haConfigurations.getMaxFailoverAttempts(), getServiceRole());
      if ( inboundResponse != null ) {
        writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
      } else {
        throw new IOException(exception);
      }
    }
  }
}
