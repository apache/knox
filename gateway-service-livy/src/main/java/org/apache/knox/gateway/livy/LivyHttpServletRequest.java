/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.livy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRequestStream;
import org.apache.knox.gateway.security.SubjectUtils;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * HttpServletRequest that adds or sets the proxyUser parameter on the json body.
 */
public class LivyHttpServletRequest extends HttpServletRequestWrapper {
  private final List<String> proxyUserEndpoints = Arrays.asList("/batches", "/sessions");

  public LivyHttpServletRequest(HttpServletRequest request) {
    super(request);
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    ServletInputStream inputStream = super.getInputStream();

    HttpServletRequest request = (HttpServletRequest) getRequest();
    String requestURI = request.getRequestURI();
    if (matchProxyUserEndpoints(requestURI)) {
      ObjectMapper objectMapper = new ObjectMapper();
      Map<String, Object> jsonMap = objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});

      jsonMap.put("proxyUser", SubjectUtils.getCurrentEffectivePrincipalName());

      String s = objectMapper.writeValueAsString(jsonMap);
      return new UrlRewriteRequestStream(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    return inputStream;
  }

  private boolean matchProxyUserEndpoints(String requestURI) {
    for (String endpoint : proxyUserEndpoints) {
      if (requestURI.endsWith(endpoint) || requestURI.endsWith(endpoint + '/')) {
        return true;
      }
    }
    return false;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
