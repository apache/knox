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
package org.apache.hadoop.gateway.hdfs.dispatch;

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.ha.provider.HaProvider;
import org.apache.hadoop.gateway.ha.provider.HaServiceConfig;
import org.apache.hadoop.gateway.ha.provider.HaServletContextListener;
import org.apache.hadoop.gateway.hdfs.i18n.WebHdfsMessages;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

public class WebHdfsHaHttpClientDispatch extends HdfsDispatch {

  private static final String FAILOVER_COUNTER_ATTRIBUTE = "dispatch.ha.failover.counter";

   private static final String RETRY_COUNTER_ATTRIBUTE = "dispatch.ha.retry.counter";

   public static final String RESOURCE_ROLE_ATTRIBUTE = "resource.role";

   private static final WebHdfsMessages LOG = MessagesFactory.get(WebHdfsMessages.class);

   private int maxFailoverAttempts;

   private int failoverSleep;

   private int maxRetryAttempts;

   private int retrySleep;

   private String resourceRole;

   private HaProvider haProvider;

   /**
   * @throws ServletException
   */
  public WebHdfsHaHttpClientDispatch() throws ServletException {
    super();
  }

   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      super.init(filterConfig);
      resourceRole = filterConfig.getInitParameter(RESOURCE_ROLE_ATTRIBUTE);
      LOG.initializingForResourceRole(resourceRole);
      haProvider = HaServletContextListener.getHaProvider(filterConfig.getServletContext());
      HaServiceConfig serviceConfig = haProvider.getHaDescriptor().getServiceConfig(resourceRole);
      maxFailoverAttempts = serviceConfig.getMaxFailoverAttempts();
      failoverSleep = serviceConfig.getFailoverSleep();
      maxRetryAttempts = serviceConfig.getMaxRetryAttempts();
      retrySleep = serviceConfig.getRetrySleep();
   }

   @Override
   protected void executeRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
      HttpResponse inboundResponse = null;
      try {
         inboundResponse = executeOutboundRequest(outboundRequest);
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
      if (inboundResponse.getStatusLine().getStatusCode() == 403) {
         BufferedHttpEntity entity = new BufferedHttpEntity(inboundResponse.getEntity());
         inboundResponse.setEntity(entity);
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
         inboundResponse.getEntity().writeTo(outputStream);
         String body = new String(outputStream.toByteArray());
         if (body.contains("StandbyException")) {
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
      AtomicInteger counter = (AtomicInteger) inboundRequest.getAttribute(FAILOVER_COUNTER_ATTRIBUTE);
      if (counter == null) {
         counter = new AtomicInteger(0);
      }
      inboundRequest.setAttribute(FAILOVER_COUNTER_ATTRIBUTE, counter);
      if (counter.incrementAndGet() <= maxFailoverAttempts) {
         haProvider.markFailedURL(resourceRole, outboundRequest.getURI().toString());
         //null out target url so that rewriters run again
         inboundRequest.setAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME, null);
         URI uri = getDispatchUrl(inboundRequest);
         ((HttpRequestBase) outboundRequest).setURI(uri);
         if (failoverSleep > 0) {
            try {
               Thread.sleep(failoverSleep);
            } catch (InterruptedException e) {
               LOG.failoverSleepFailed(resourceRole, e);
            }
         }
         executeRequest(outboundRequest, inboundRequest, outboundResponse);
      } else {
         LOG.maxFailoverAttemptsReached(maxFailoverAttempts, resourceRole);
         if (inboundResponse != null) {
            writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
         } else {
            throw new IOException(exception);
         }
      }
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
               LOG.retrySleepFailed(resourceRole, e);
            }
         }
         executeRequest(outboundRequest, inboundRequest, outboundResponse);
      } else {
         LOG.maxRetryAttemptsReached(maxRetryAttempts, resourceRole, outboundRequest.getURI().toString());
         if (inboundResponse != null) {
            writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
         } else {
            throw new IOException(exception);
         }
      }
   }
}
