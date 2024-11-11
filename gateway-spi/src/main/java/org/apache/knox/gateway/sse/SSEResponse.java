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

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHttpResponse;

import java.util.Locale;

public class SSEResponse extends BasicHttpResponse {
    private final HttpResponse inboundResponse;
    private final SSEEntity entity;

    public SSEResponse(HttpResponse inboundResponse) {
        super(inboundResponse.getStatusLine());
        this.inboundResponse = inboundResponse;
        this.entity = new SSEEntity(inboundResponse.getEntity());
    }

    @Override
    public SSEEntity getEntity() {
        return entity;
    }

    @Override
    public StatusLine getStatusLine() {
        return inboundResponse.getStatusLine();
    }

    @Override
    public void setStatusLine(StatusLine statusline) {
        inboundResponse.setStatusLine(statusline);
    }

    @Override
    public void setStatusLine(ProtocolVersion ver, int code) {
        inboundResponse.setStatusLine(ver, code);
    }

    @Override
    public void setStatusLine(ProtocolVersion ver, int code, String reason) {
        inboundResponse.setStatusLine(ver, code, reason);
    }

    @Override
    public void setStatusCode(int code) throws IllegalStateException {
        inboundResponse.setStatusCode(code);
    }

    @Override
    public void setReasonPhrase(String reason) throws IllegalStateException {
        inboundResponse.setReasonPhrase(reason);
    }

    @Override
    public Locale getLocale() {
        return inboundResponse.getLocale();
    }

    @Override
    public void setLocale(Locale loc) {
        inboundResponse.setLocale(loc);
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return inboundResponse.getProtocolVersion();
    }

    @Override
    public boolean containsHeader(String name) {
        return inboundResponse.containsHeader(name);
    }

    @Override
    public Header[] getHeaders(String name) {
        return inboundResponse.getHeaders(name);
    }

    @Override
    public Header getFirstHeader(String name) {
        return inboundResponse.getFirstHeader(name);
    }

    @Override
    public Header getLastHeader(String name) {
        return inboundResponse.getLastHeader(name);
    }

    @Override
    public Header[] getAllHeaders() {
        return inboundResponse.getAllHeaders();
    }

    @Override
    public void addHeader(Header header) {
        inboundResponse.addHeader(header);
    }

    @Override
    public void addHeader(String name, String value) {
        inboundResponse.addHeader(name, value);
    }

    @Override
    public void setHeader(Header header) {
        inboundResponse.setHeader(header);
    }

    @Override
    public void setHeader(String name, String value) {
        inboundResponse.setHeader(name, value);
    }

    @Override
    public void setHeaders(Header[] headers) {
        inboundResponse.setHeaders(headers);
    }

    @Override
    public void removeHeader(Header header) {
        inboundResponse.removeHeader(header);
    }

    @Override
    public void removeHeaders(String name) {
        inboundResponse.removeHeaders(name);
    }

    @Override
    public HeaderIterator headerIterator() {
        return inboundResponse.headerIterator();
    }

    @Override
    public HeaderIterator headerIterator(String name) {
        return inboundResponse.headerIterator(name);
    }
}
