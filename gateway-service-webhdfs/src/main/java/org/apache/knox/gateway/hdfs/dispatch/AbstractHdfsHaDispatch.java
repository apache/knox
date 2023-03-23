/*
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
package org.apache.knox.gateway.hdfs.dispatch;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.ha.dispatch.ConfigurableHADispatch;
import org.apache.knox.gateway.hdfs.i18n.WebHdfsMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractHdfsHaDispatch extends ConfigurableHADispatch {

  private static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";
  private static final WebHdfsMessages LOG = MessagesFactory.get(WebHdfsMessages.class);

  public AbstractHdfsHaDispatch() throws ServletException {
    super();
  }

  @Override
  public void init() {
     super.init();
   }

  abstract String getResourceRole();


  @Override
  protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
      HttpResponse inboundResponse = null;
      try {
         inboundResponse = executeOutboundRequest(outboundRequest);
         writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
      } catch (StandbyException | SafeModeException | IOException e) {
        /* if non-idempotent requests are not allowed to failover */
        if(!failoverNonIdempotentRequestEnabled && nonIdempotentRequests.stream().anyMatch(outboundRequest.getMethod()::equalsIgnoreCase)) {
          LOG.cannotFailoverNonIdempotentRequest(outboundRequest.getMethod(), e.toString());
          throw e;
        } else {
          if(e instanceof StandbyException) {
            LOG.errorReceivedFromStandbyNode(e);
          } else if(e instanceof SafeModeException) {
            LOG.errorReceivedFromSafeModeNode(e);
          } else {
            LOG.errorConnectingToServer(outboundRequest.getURI().toString(), e);
          }
          failoverRequest(outboundRequest, inboundRequest, outboundResponse,
              inboundResponse, e);
        }
      }
   }

  /**
    * Checks for specific outbound response codes/content to trigger a retry or failover
    */
  @Override
  protected void writeOutboundResponse(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse) throws IOException {
      if (inboundResponse.getStatusLine().getStatusCode() == 403) {
         BufferedHttpEntity entity = new BufferedHttpEntity(inboundResponse.getEntity());
         inboundResponse.setEntity(entity);
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
         inboundResponse.getEntity().writeTo(outputStream);
         String body = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
         if (body.contains("StandbyException")) {
            throw new StandbyException();
         }
         if (body.contains("SafeModeException") || body.contains("RetriableException")) {
            throw new SafeModeException();
         }
      }
      super.writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
   }

  @Override
  protected void failoverRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse, Exception exception) throws IOException {
      LOG.failedToConnectTo(outboundRequest.getURI().toString());
      AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
      if (counter == null) {
         counter = new AtomicInteger(0);
      }
      inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
      if (counter.incrementAndGet() <= maxFailoverAttempts) {
         haProvider.markFailedURL(getResourceRole(), outboundRequest.getURI().toString());
         //null out target url so that rewriters run again
         inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);
         URI uri = getDispatchUrl(inboundRequest);
         ((HttpRequestBase) outboundRequest).setURI(uri);
         if (failoverSleep > 0) {
            try {
               Thread.sleep(failoverSleep);
            } catch (InterruptedException e) {
               LOG.failoverSleepFailed(getResourceRole(), e);
               Thread.currentThread().interrupt();
            }
         }
         LOG.failingOverRequest(outboundRequest.getURI().toString());
         executeRequest(outboundRequest, inboundRequest, outboundResponse);
      } else {
         LOG.maxFailoverAttemptsReached(maxFailoverAttempts, getResourceRole());
         if (inboundResponse != null) {
            writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
         } else {
            throw new IOException(exception);
         }
      }
   }

  /**
   * This method ensures that the request InputStream is not acquired
   * prior to a dispatch to a component such as a namenode that doesn't
   * the request body. The side effect of this is that the client does
   * not get a 100 continue from Knox which will trigger the client to
   * send the entire payload before redirect to the target component
   * like a datanode and have to send it again.
   */
  @Override
  protected HttpEntity createRequestEntity(HttpServletRequest request)
      throws IOException {
    return null;
  }
}
