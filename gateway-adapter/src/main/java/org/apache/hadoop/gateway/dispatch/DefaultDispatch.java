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
package org.apache.hadoop.gateway.dispatch;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

@Deprecated
public class DefaultDispatch extends org.apache.knox.gateway.dispatch.DefaultDispatch {
  @Override
  public void init() {
    super.init();
  }

  @Override
  public void destroy() {
    super.destroy();
  }

  @Override
  protected int getReplayBufferSize() {
    return super.getReplayBufferSize();
  }

  @Override
  protected void setReplayBufferSize(int size) {
    super.setReplayBufferSize(size);
  }

  @Override
  protected int getReplayBufferSizeInBytes() {
    return super.getReplayBufferSizeInBytes();
  }

  @Override
  protected void setReplayBufferSizeInBytes(int size) {
    super.setReplayBufferSizeInBytes(size);
  }

  @Override
  protected void executeRequest(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
      throws IOException {
    super.executeRequest(outboundRequest, inboundRequest, outboundResponse);
  }

  @Override
  protected HttpResponse executeOutboundRequest(HttpUriRequest outboundRequest)
      throws IOException {
    return super.executeOutboundRequest(outboundRequest);
  }

  @Override
  protected void writeOutboundResponse(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest, HttpServletResponse outboundResponse,
      HttpResponse inboundResponse) throws IOException {
    super.writeOutboundResponse(outboundRequest, inboundRequest,
        outboundResponse, inboundResponse);
  }

  @Override
  protected void closeInboundResponse(HttpResponse response, InputStream stream)
      throws IOException {
    super.closeInboundResponse(response, stream);
  }

  /**
   * This method provides a hook for specialized credential propagation
   * in subclasses.
   *
   * @param outboundRequest
   */
  @Override
  protected void addCredentialsToRequest(HttpUriRequest outboundRequest) {
    super.addCredentialsToRequest(outboundRequest);
  }

  @Override
  protected HttpEntity createRequestEntity(HttpServletRequest request)
      throws IOException {
    return super.createRequestEntity(request);
  }

  @Override
  public void doGet(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doGet(url, request, response);
  }

  @Override
  public void doOptions(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doOptions(url, request, response);
  }

  @Override
  public void doPut(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doPut(url, request, response);
  }

  @Override
  public void doPost(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doPost(url, request, response);
  }

  @Override
  public void doDelete(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doDelete(url, request, response);
  }

  @Override
  public void doHead(URI url, HttpServletRequest request,
      HttpServletResponse response) throws IOException, URISyntaxException {
    super.doHead(url, request, response);
  }

  @Override
  public Set<String> getOutboundResponseExcludeHeaders() {
    return super.getOutboundResponseExcludeHeaders();
  }
}
