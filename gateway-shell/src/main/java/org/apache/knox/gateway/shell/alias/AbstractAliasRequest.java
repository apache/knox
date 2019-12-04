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
package org.apache.knox.gateway.shell.alias;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.ErrorResponse;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.KnoxShellException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public abstract class AbstractAliasRequest extends AbstractRequest<AliasResponse> {

  public static final String SERVICE_PATH = "/admin/api/v1/aliases";

  protected static final String GATEWAY_CLUSTER_NAME = "__gateway";

  protected enum RequestType { GET, PUT, POST, DELETE }

  private HttpRequestBase httpRequest;

  protected URI requestURI;

  protected String clusterName;

  protected abstract RequestType getRequestType();

  AbstractAliasRequest(final KnoxSession session) {
    this(session, null, null);
  }

  AbstractAliasRequest(final KnoxSession session, final String clusterName) {
    this(session, clusterName, null);
  }

  AbstractAliasRequest(final KnoxSession session, final String clusterName, final String doAsUser) {
    super(session, doAsUser);
    this.clusterName = clusterName != null ? clusterName : GATEWAY_CLUSTER_NAME;
  }


  public URI getRequestURI() {
    return requestURI;
  }

  public HttpRequestBase getRequest() {
    return httpRequest;
  }

  @Override
  protected Callable<AliasResponse> callable() {
    return () -> {
      httpRequest = createRequest();
      try {
        return createResponse(execute(httpRequest));
      } catch (ErrorResponse e) {
        return new AliasResponse(e.getResponse());
      }
    };
  }

  protected URI buildURI() {
    try {
      URIBuilder uri = uri(getPathElements().toArray(new String[]{}));
      return uri.build();
    } catch (URISyntaxException e) {
      throw new KnoxShellException(e);
    }
  }

  protected List<String> getPathElements() {
    List<String> elements = new ArrayList<>();
    elements.add(SERVICE_PATH);
    if (clusterName != null) {
      elements.add("/");
      elements.add(clusterName);
    }
    return elements;
  }

  protected HttpRequestBase createRequest() {
    HttpRequestBase request;

    switch (getRequestType()) {
      case POST:
        request = new HttpPost(requestURI);
        break;
      case PUT:
        request = new HttpPut(requestURI);
        break;
      case DELETE:
        request = new HttpDelete(requestURI);
        break;
      case GET:
      default:
        request = new HttpGet(requestURI);
        break;
    }
    return request;
  }

  protected AliasResponse createResponse(HttpResponse response) {
    return new AliasResponse(response);
  }

}
