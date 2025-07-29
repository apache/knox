/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.sse;

import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SSECallback implements FutureCallback<SSEResponse> {

    protected static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
    protected final AsyncContext asyncContext;
    private final HttpAsyncRequestProducer producer;
    protected final HttpServletResponse outboundResponse;


    public SSECallback(HttpServletResponse outboundResponse, AsyncContext asyncContext, HttpAsyncRequestProducer producer) {
        this.outboundResponse = outboundResponse;
        this.asyncContext = asyncContext;
        this.producer = producer;
    }

    @Override
    public void completed(final SSEResponse response) {
        releaseResources(true);
        LOG.sseConnectionDone();
    }

    @Override
    public void failed(final Exception ex) {
        try {
            if(!outboundResponse.isCommitted()) {
                outboundResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Service connection error");
            }
        } catch (Exception e) {
            LOG.failedToSendErrorToClient(e);
        } finally {
            releaseResources(true);
            LOG.sseConnectionError(ex.getMessage());
        }
    }

    @Override
    public void cancelled() {
        releaseResources(true);
        LOG.sseConnectionCancelled();
    }

    protected void releaseResources(boolean releaseContext) {
        try {
            producer.close();
        } catch (IOException e) {
            LOG.sseProducerCloseError(e);
        } finally {
            if (releaseContext) {
                asyncContext.complete();
            }
        }
    }
}
