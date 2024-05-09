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
package org.apache.knox.gateway.util;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.knox.test.mock.MockServletInputStream;
import org.easymock.EasyMock;
import org.junit.Test;

public class RequestBodyUtilsTest {

  private static final String REQUEST_BODY_PARAM_NAME = "myParam";
  private static final String REQUEST_BODY_PARAM_VALUE_RAW = "This-is_my sample text!";
  private static final String REQUEST_BODY_PARAM_VALUE_ENCODED = "This-is_my%20sample%20text%21";

  @Test
  public void testGetRequestBodyParameterEncoded() throws Exception {
    testGetRequestBodyParameter(true);
  }

  @Test
  public void testGetRequestBodyParameterRaw() throws Exception {
    testGetRequestBodyParameter(false);
  }

  private void testGetRequestBodyParameter(boolean decode) throws Exception {
    final ServletRequest request = EasyMock.createNiceMock(ServletRequest.class);
    EasyMock.expect(request.getInputStream()).andReturn(produceServletInputStream(decode)).anyTimes();

    EasyMock.replay(request);

    final String requestBodyParam = RequestBodyUtils.getRequestBodyParameter(request, REQUEST_BODY_PARAM_NAME, decode);
    assertEquals(REQUEST_BODY_PARAM_VALUE_RAW, requestBodyParam);
  }

  private ServletInputStream produceServletInputStream(boolean encode) {
    final String requestBody = REQUEST_BODY_PARAM_NAME + "=" + (encode ? REQUEST_BODY_PARAM_VALUE_ENCODED : REQUEST_BODY_PARAM_VALUE_RAW);
    final InputStream inputStream = IOUtils.toInputStream(requestBody, StandardCharsets.UTF_8);
    return new MockServletInputStream(inputStream);
  }

}
