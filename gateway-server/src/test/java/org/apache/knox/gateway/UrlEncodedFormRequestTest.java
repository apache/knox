/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.knox.test.mock.MockHttpServletRequest;
import org.apache.knox.test.mock.MockServletInputStream;
import org.eclipse.jetty.io.RuntimeIOException;
import org.junit.Test;

public class UrlEncodedFormRequestTest {

  @Test
  public void testParametersAreComingFromQueryStringOnly() throws Exception {
    MockHttpServletRequest originalRequest = makeRequest("a=1&b=2", "query1=x&query2=y&query2=y2");
    assertEquals("1", originalRequest.getParameter("a"));
    assertEquals("2", originalRequest.getParameter("b"));
    UrlEncodedFormRequest wrappedRequest = new UrlEncodedFormRequest(originalRequest);
    assertEquals("x", wrappedRequest.getParameter("query1"));
    assertEquals("y", wrappedRequest.getParameter("query2"));
    assertNull(wrappedRequest.getParameter("a"));
    assertNull(wrappedRequest.getParameter("b"));
    assertArrayEquals(new String[]{"x"}, wrappedRequest.getParameterValues("query1"));
    assertArrayEquals(new String[]{"y", "y2"}, wrappedRequest.getParameterValues("query2"));
    assertEquals(Arrays.asList("query1", "query2"), Collections.list(wrappedRequest.getParameterNames()));
    assertArrayEquals(new String[]{"x"}, wrappedRequest.getParameterMap().get("query1"));
    assertArrayEquals(new String[]{"y", "y2"}, wrappedRequest.getParameterMap().get("query2"));
    assertNull(wrappedRequest.getParameterValues("unknown"));
  }

  private static MockHttpServletRequest makeRequest(String body, String queryString) {
    MockHttpServletRequest request = new MockHttpServletRequest() {
      private boolean parametersExtracted;
      private Map<String, String> params = new HashMap<>();

      @Override
      public String getParameter(String name) { // mimic how the real request works
        if (!parametersExtracted) {
          try {
            String body = IOUtils.toString(getInputStream(), StandardCharsets.UTF_8);
            for (String parts : body.split("\\&")) {
              params.put(parts.split("=")[0], parts.split("=")[1]);
            }
            parametersExtracted = true;
          } catch (IOException e) {
            throw new RuntimeIOException(e);
          }
        }
        return params.get(name);
      }
    };
    request.setQueryString(queryString);
    request.setInputStream(new MockServletInputStream(
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
    return request;
  }
}