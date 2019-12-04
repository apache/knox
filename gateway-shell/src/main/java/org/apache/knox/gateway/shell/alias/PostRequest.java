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
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.message.BasicNameValuePair;
import org.apache.knox.gateway.shell.KnoxSession;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PostRequest extends AbstractAliasRequest {

  static final String FORM_PARAM_VALUE = "value";

  private String alias;
  private String pwd;

  PostRequest(final KnoxSession session, final String alias, final String pwd) {
    this(session, null, alias, pwd);
  }

  PostRequest(final KnoxSession session, final String clusterName, final String alias, final String pwd) {
    this(session, clusterName, alias, pwd, null);
  }

  PostRequest(final KnoxSession session,
              final String      clusterName,
              final String      alias,
              final String      pwd,
              final String      doAsUser) {
    super(session, clusterName, doAsUser);
    this.alias = alias;
    this.pwd   = pwd;
    requestURI = buildURI();
  }

  @Override
  protected RequestType getRequestType() {
    return RequestType.POST;
  }

  @Override
  protected List<String> getPathElements() {
    List<String> elements = super.getPathElements();
    elements.add("/");
    elements.add(alias);
    return elements;
  }

  @Override
  protected HttpRequestBase createRequest() {
    HttpRequestBase request = super.createRequest();
    List<NameValuePair> formData = new ArrayList<>();
    formData.add(new BasicNameValuePair(FORM_PARAM_VALUE, pwd));
    ((HttpPost) request).setEntity(new UrlEncodedFormEntity(formData, StandardCharsets.UTF_8));
    return request;
  }

  @Override
  protected AliasResponse createResponse(HttpResponse response) {
    return new AddAliasResponse(response);
  }

}
