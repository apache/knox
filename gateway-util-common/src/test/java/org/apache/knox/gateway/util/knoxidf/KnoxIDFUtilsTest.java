/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util.knoxidf;

import jakarta.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnoxIDFUtilsTest {

  @Test
  public void testIsAuthorizeRequest() {
    assertTrue(isAuthorizeRequest("/gateway/knoxidf/knoxidf/api/v1/authorize"));
    assertTrue(isAuthorizeRequest("/gateway/knoxidf/knoxidf/api/v1/authorize/"));
    assertFalse(isAuthorizeRequest("/gateway/knoxidf/knoxidf/api/v1/authorize/callback"));
    assertFalse(isAuthorizeRequest("/gateway/knoxidf/knoxidf/api/v1/authorize/consentAccepted"));
    assertFalse(isAuthorizeRequest("/gateway/knoxidf/knoxidf/api/v1/token"));
    assertFalse(isAuthorizeRequest("/gateway/knoxidf/app/authorize"));
    assertFalse(isAuthorizeRequest("/gateway/knoxidf/xknoxidf/api/v1/authorize"));
    assertFalse(isAuthorizeRequest(null));
  }

  private static boolean isAuthorizeRequest(String requestUri) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURI()).andReturn(requestUri).anyTimes();
    EasyMock.replay(request);
    return KnoxIDFUtils.isAuthorizeRequest(request);
  }
}
