package org.apache.hadoop.gateway.rm.dispatch;
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.apache.hadoop.gateway.dispatch.DefaultDispatch;
import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.ha.provider.HaProvider;
import org.apache.hadoop.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.rm.i18n.RMMessages;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

class  RMHaBaseDispatcher extends DefaultDispatch {
    private static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";
    private static final String RETRY_COUNTER_ATTRIBUTE = "dispatch.ha.retry.counter";
    private static final String LOCATION = "Location";
    private static final RMMessages LOG = MessagesFactory.get(RMMessages.class);
    private int maxFailoverAttempts = HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS;
    private int failoverSleep = HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP;
    private int maxRetryAttempts = HaServiceConfigConstants.DEFAULT_MAX_RETRY_ATTEMPTS;
    private int retrySleep = HaServiceConfigConstants.DEFAULT_RETRY_SLEEP;
    private String resourceRole = null;
    private HttpResponse inboundResponse = null;

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

    void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }

    void setRetrySleep(int retrySleep) {
        this.retrySleep = retrySleep;
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
        } catch (SafeModeException e) {
           LOG.errorReceivedFromSafeModeNode(e);
           retryRequest(outboundRequest, inboundRequest, outboundResponse, inboundResponse, e);
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
          String body = new String(outputStream.toByteArray());
          if (body.contains("This is standby RM")) {
             throw new StandbyException();
          }
          if (body.contains("SafeModeException") || body.contains("RetriableException")) {
             throw new SafeModeException();
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

    private void retryRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
       LOG.retryingRequest(outboundRequest.getURI().toString());
       AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(RETRY_COUNTER_ATTRIBUTE);
       if (counter == null) {
          counter = new AtomicInteger(0);
       }
       inboundRequest.setAttribute(RETRY_COUNTER_ATTRIBUTE, counter);
       if (counter.incrementAndGet() <= maxRetryAttempts) {
          if (retrySleep > 0) {
             try {
                Thread.sleep(retrySleep);
             } catch (InterruptedException e) {
                LOG.retrySleepFailed(this.resourceRole, e);
             }
          }
          executeRequest(outboundRequest, inboundRequest, outboundResponse);
       } else {
          LOG.maxRetryAttemptsReached(maxRetryAttempts, this.resourceRole, outboundRequest.getURI().toString());
          if (inboundResponse != null) {
             writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
          } else {
             throw new IOException(exception);
          }
       }
    }
}
