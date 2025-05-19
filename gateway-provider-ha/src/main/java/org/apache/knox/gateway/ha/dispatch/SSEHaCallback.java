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
package org.apache.knox.gateway.ha.dispatch;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.knox.gateway.sse.SSECallback;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.apache.knox.gateway.util.HttpUtils.isConnectionError;

public class SSEHaCallback extends SSECallback {

    private final SSEHaDispatch dispatch;
    private final HttpUriRequest outboundRequest;
    private final HttpServletRequest inboundRequest;

    public SSEHaCallback(HttpServletResponse outboundResponse, AsyncContext asyncContext, HttpAsyncRequestProducer producer,
                         SSEHaDispatch dispatch, HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) {
        super(outboundResponse, asyncContext, producer);
        this.dispatch = dispatch;
        this.outboundRequest = outboundRequest;
        this.inboundRequest = inboundRequest;
    }

    @Override
    public void failed(final Exception ex) {
        if(outboundResponse.isCommitted()) {
            /*
                If the response was already committed, it means the connection was closed abruptly no failover needed.
                The endpoint shouldn't be marked as failed.
            */
            super.failed(ex);
        } else {
            if(!isConnectionError(ex.getCause() == null ? ex : ex.getCause()) && dispatch.isNonIdempotentAndNonIdempotentFailoverDisabled(outboundRequest)) {
                dispatch.markEndpointFailed(outboundRequest, inboundRequest);
                super.failed(ex);
            } else {
                releaseResources(false);
                LOG.sseConnectionError(ex.getMessage());
                dispatch.failoverRequest(outboundRequest, outboundResponse, inboundRequest, asyncContext);
            }
        }
    }
}
