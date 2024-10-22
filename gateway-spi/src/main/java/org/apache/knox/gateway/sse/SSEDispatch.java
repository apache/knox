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

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.client.methods.AsyncCharConsumer;
import org.apache.http.nio.client.methods.HttpAsyncMethods;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;
import org.apache.knox.gateway.dispatch.DefaultHttpAsyncClientFactory;
import org.apache.knox.gateway.dispatch.HttpAsyncClientFactory;

import javax.servlet.AsyncContext;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

public class SSEDispatch extends ConfigurableDispatch {

    private final HttpAsyncClient asyncClient;
    private static final String TEXT_EVENT_STREAM_VALUE = "text/event-stream";

    public SSEDispatch(FilterConfig filterConfig) {
        HttpAsyncClientFactory asyncClientFactory = new DefaultHttpAsyncClientFactory();
        this.asyncClient = asyncClientFactory.createAsyncHttpClient(filterConfig);

        if (asyncClient instanceof CloseableHttpAsyncClient) {
            ((CloseableHttpAsyncClient) this.asyncClient).start();
        }
    }

    @Override
    public void doGet(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final HttpGet httpGetRequest = new HttpGet(url);
        this.doHttpMethod(httpGetRequest, inboundRequest, outboundResponse);
    }

    @Override
    public void doPost(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException, URISyntaxException {
        final HttpPost httpPostRequest = new HttpPost(url);
        httpPostRequest.setEntity(this.createRequestEntity(inboundRequest));
        this.doHttpMethod(httpPostRequest, inboundRequest, outboundResponse);
    }

    @Override
    public void doPut(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final HttpPut httpPutRequest = new HttpPut(url);
        httpPutRequest.setEntity(this.createRequestEntity(inboundRequest));
        this.doHttpMethod(httpPutRequest, inboundRequest, outboundResponse);
    }

    @Override
    public void doPatch(URI url, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
            throws IOException {
        final HttpPatch httpPatchRequest = new HttpPatch(url);
        httpPatchRequest.setEntity(this.createRequestEntity(inboundRequest));
        this.doHttpMethod(httpPatchRequest, inboundRequest, outboundResponse);
    }

    private void doHttpMethod(HttpUriRequest httpMethod, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) throws IOException {
        this.addAcceptHeader(httpMethod);
        this.copyRequestHeaderFields(httpMethod, inboundRequest);
        this.executeRequest(httpMethod, outboundResponse, inboundRequest);
    }

    private void executeRequest(HttpUriRequest outboundRequest, HttpServletResponse outboundResponse, HttpServletRequest inboundRequest) {
        AsyncContext asyncContext = inboundRequest.startAsync();
        //No timeout
        asyncContext.setTimeout(0L);

        HttpAsyncRequestProducer producer = HttpAsyncMethods.create(outboundRequest);
        AsyncCharConsumer<SSEResponse> consumer = new SSECharConsumer(outboundResponse, outboundRequest.getURI(), asyncContext);
        this.executeAsyncRequest(producer, consumer, outboundRequest);
    }

    private void executeAsyncRequest(HttpAsyncRequestProducer producer, AsyncCharConsumer<SSEResponse> consumer, HttpUriRequest outboundRequest) {
        LOG.dispatchRequest(outboundRequest.getMethod(), outboundRequest.getURI());
        auditor.audit(Action.DISPATCH, outboundRequest.getURI().toString(), ResourceType.URI, ActionOutcome.UNAVAILABLE, RES.requestMethod(outboundRequest.getMethod()));
        asyncClient.execute(producer, consumer, new FutureCallback<SSEResponse>() {

            @Override
            public void completed(final SSEResponse response) {
                closeProducer(producer);
                LOG.sseConnectionDone();
            }

            @Override
            public void failed(final Exception ex) {
                closeProducer(producer);
                LOG.sseConnectionError(ex.getMessage());
            }

            @Override
            public void cancelled() {
                closeProducer(producer);
                LOG.sseConnectionCancelled();
            }
        });
    }

    private void addAcceptHeader(HttpUriRequest outboundRequest) {
        outboundRequest.setHeader(HttpHeaders.ACCEPT, SSEDispatch.TEXT_EVENT_STREAM_VALUE);
    }

    private void handleSuccessResponse(HttpServletResponse outboundResponse, URI url, HttpResponse inboundResponse) {
        this.prepareServletResponse(outboundResponse, inboundResponse.getStatusLine().getStatusCode());
        this.copyResponseHeaderFields(outboundResponse, inboundResponse);
        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.SUCCESS, RES.responseStatus(HttpStatus.SC_OK));
    }

    private void handleErrorResponse(HttpServletResponse outboundResponse, URI url, HttpResponse httpResponse) {
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        outboundResponse.setStatus(statusCode);
        LOG.dispatchResponseStatusCode(statusCode);
        auditor.audit(Action.DISPATCH, url.toString(), ResourceType.URI, ActionOutcome.FAILURE, RES.responseStatus(statusCode));
    }

    private void prepareServletResponse(HttpServletResponse outboundResponse, int statusCode) {
        LOG.dispatchResponseStatusCode(statusCode);
        outboundResponse.setStatus(statusCode);
        outboundResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
    }

    private boolean isSuccessful(int statusCode) {
        return (statusCode >= HttpStatus.SC_OK && statusCode < 300);
    }

    private void closeProducer(HttpAsyncRequestProducer producer) {
        try {
            producer.close();
        } catch (IOException e) {
            LOG.sseProducerCloseError(e);
        }
    }

    private class SSECharConsumer extends AsyncCharConsumer<SSEResponse> {
        private SSEResponse sseResponse;
        private final HttpServletResponse outboundResponse;
        private final URI url;
        private final AsyncContext asyncContext;

        SSECharConsumer(HttpServletResponse outboundResponse, URI url, AsyncContext asyncContext) {
            this.outboundResponse = outboundResponse;
            this.url = url;
            this.asyncContext = asyncContext;
        }

        @Override
        protected void onResponseReceived(final HttpResponse inboundResponse) {
            this.sseResponse = new SSEResponse(inboundResponse);
            if (isSuccessful(inboundResponse.getStatusLine().getStatusCode())) {
                handleSuccessResponse(outboundResponse, url, inboundResponse);
            } else {
                handleErrorResponse(outboundResponse, url, inboundResponse);
            }
        }

        @Override
        protected void onCharReceived(final CharBuffer buf, final IOControl ioctl) {
            try {
                if (this.sseResponse.getEntity().readCharBuffer(buf)) {
                    this.sseResponse.getEntity().sendEvent(this.asyncContext);
                }
            } catch (InterruptedException | IOException e) {
                LOG.errorWritingOutputStream(e);
                throw new SSEException(e.getMessage(), e);
            }
        }

        @Override
        protected void releaseResources() {
            this.asyncContext.complete();
        }

        @Override
        protected SSEResponse buildResult(final HttpContext context) {
            return this.sseResponse;

        }
    }

    @Override
    public void destroy() {
        try {
            if (this.asyncClient != null && asyncClient instanceof CloseableHttpAsyncClient) {
                ((CloseableHttpAsyncClient) asyncClient).close();
            }
        } catch (IOException e) {
            LOG.errorClosingHttpClient(e);
        }
    }

    public HttpAsyncClient getAsyncClient() {
        return this.asyncClient;
    }
}
