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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.KnoxShellException;

import java.util.Map;

public class AliasResponse extends BasicResponse {

  private static final String CONTENT_TYPE = "application/json";

  private String responseContent;

  protected Map<String, Object> parsedResponse;

  protected String cluster;


  AliasResponse(HttpResponse response) {
    super(response);
    parseResponseEntity();
  }

  private void parseResponseEntity() {
    if (!isExpectedResponseStatus()) {
      throw new KnoxShellException("Unexpected response: " + response().getStatusLine().getReasonPhrase());
    }

    HttpEntity entity = response().getEntity();
    if (entity == null) {
      throw new KnoxShellException("Missing expected response content");
    }

    String contentType = entity.getContentType().getValue();
    if (!CONTENT_TYPE.equals(contentType)) {
      throw new KnoxShellException("Unexpected response content type: " + contentType);
    }

    try {
      responseContent = EntityUtils.toString(entity);
      parsedResponse =
          (new ObjectMapper()).readValue(responseContent, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new KnoxShellException("Unable to process response content", e);
    }
  }

  protected boolean isExpectedResponseStatus() {
    return (response().getStatusLine().getStatusCode() == HttpStatus.SC_OK);
  }

  public String getCluster() {
    return cluster;
  }

  public String getResponseContent() {
    return responseContent;
  }

}
