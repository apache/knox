/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.rm.dispatch;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.rm.i18n.RMMessages;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

class  RMHaBaseDispatcher extends ConfigurableDispatch {
    private static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";
    private static final String LOCATION = "Location";
    private static final RMMessages LOG = MessagesFactory.get(RMMessages.class);
    private int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;
    private int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;
    private String resourceRole;
    private HttpResponse inboundResponse;

    /**
     *
     * @return HttpReponse used for unit testing so we
     * can inject inboundResponse before calling executeRequest method
     */
    private HttpResponse getInboundResponse() {
        HttpResponse response = this.inboundResponse;
        this.setInboundResponse(null);
        return response;
    }

    void setInboundResponse(HttpResponse inboundResponse) {
        this.inboundResponse = inboundResponse;
    }

    void setHaProvider(HaProvider haProvider) {
        this.haProvider = haProvider;
    }

    private HaProvider haProvider;

    void setMaxFailoverAttempts(int maxFailoverAttempts) {
        this.maxFailoverAttempts = maxFailoverAttempts;
    }

    void setFailoverSleep(int failoverSleep) {
        this.failoverSleep = failoverSleep;
    }

    void setResourceRole(String resourceRole) {
        this.resourceRole = resourceRole;
    }

    @Override
     protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
        HttpResponse inboundResponse = this.getInboundResponse();
        try {
           if( this.getInboundResponse() == null ) {
             inboundResponse = executeOutboundRequest(outboundRequest);
           }
           writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
        } catch (StandbyException e) {
           LOG.errorReceivedFromStandbyNode(e);
           failoverRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
        } catch (IOException e) {
           LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
           failoverRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
        }
     }

    /**
     * Checks for specific outbound response codes/content to trigger a retry or failover
     */
    @Override
    protected void writeOutboundResponse(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse) throws IOException {
       int status = inboundResponse.getStatusLine().getStatusCode();
       if ( status  == 403 || status == 307) {
          BufferedHttpEntity entity = new BufferedHttpEntity(inboundResponse.getEntity());
          inboundResponse.setEntity(entity);
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
          inboundResponse.getEntity().writeTo(outputStream);
          String body = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
          if (body.contains("This is standby RM")) {
             throw new StandbyException();
          }
       }
       super.writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
    }

    private void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
       LOG.failingOverRequest(outboundRequest.getURI().toString());
       URI uri;
       String outboundURIs;
       AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
       if (counter == null) {
          counter = new AtomicInteger(0);
       }
       inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
        outboundURIs = outboundRequest.getURI().toString();

       if (counter.incrementAndGet() <= maxFailoverAttempts) {
          //null out target url so that rewriters run again
          inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);

           uri = getUriFromInbound(inboundRequest, inboundResponse, outboundURIs);
           ((HttpRequestBase) outboundRequest).setURI(uri);
          if (failoverSleep > 0) {
             try {
                Thread.sleep(failoverSleep);
             } catch (InterruptedException e) {
                LOG.failoverSleepFailed(this.resourceRole, e);
                Thread.currentThread().interrupt();
             }
          }
          executeRequest(outboundRequest, inboundRequest, outboundResponse);
       } else {
          LOG.maxFailoverAttemptsReached(maxFailoverAttempts, this.resourceRole);
          if (inboundResponse != null) {
             writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
          } else {
             throw new IOException(exception);
          }
       }
    }

    URI getUriFromInbound(HttpServletRequest inboundRequest, HttpResponse inboundResponse, String outboundURIs) {
         URI uri;
         if( outboundURIs != null ) {
           markFailedURL(outboundURIs);
         }
         try {
            if( inboundResponse != null ) {
               // We get redirection URI, failover condition we don't
               // need to consult list of host.
               String host = inboundResponse.getFirstHeader(LOCATION).getValue();
               LOG.failoverRedirect(host);
               uri = URI.create(host);
            } else { // inboundRequest was null previous active node is down
                     // get next URI in list to try.
               uri = getDispatchUrl(inboundRequest);
            }
         } catch(Exception ex ) {
            uri = getDispatchUrl(inboundRequest);
         }
         haProvider.setActiveURL(this.resourceRole, uri.toString());
         return uri;
     }

    private void markFailedURL(String outboundURIs) {
        haProvider.markFailedURL(this.resourceRole, outboundURIs);
    }
}
