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
package org.apache.knox.gateway.service.knoxidf;

import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.ws.rs.core.Response;

import org.apache.knox.gateway.util.knoxidf.AllowedGrantTypes;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.glassfish.hk2.api.ProxyCtl;
import org.junit.Test;

/**
 * TokenResource is a singleton, so its @Context request is an HK2 proxy. Form body parameters must
 * still be read from the unwrapped request behind that proxy.
 */
public class TokenResourceRequestProxyTest {

  @Test
  public void testFormParameterIsReadThroughRequestProxy() {
    final HttpServletRequest containerRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(containerRequest.getParameter(GRANT_TYPE)).andReturn(TOKEN_EXCHANGE_GRANT_TYPE).anyTimes();
    // Only allow the grant sent, so a parameter lost behind the proxy is rejected as a missing grant_type.
    EasyMock.expect(containerRequest.getAttribute(KnoxIDFConstants.ALLOWED_GRANT_TYPES_ATTRIBUTE))
        .andReturn(AllowedGrantTypes.parse(TOKEN_EXCHANGE_GRANT_TYPE)).anyTimes();
    EasyMock.replay(containerRequest);

    // Like the GatewayFilter's UrlEncodedFormRequest, this wrapper hides the form body parameters.
    final HttpServletRequest gatewayRequest = new HttpServletRequestWrapper(containerRequest) {
      @Override
      public String getParameter(String name) {
        return null;
      }
    };

    final TokenResource resource = new TokenResource() {
      @Override
      public Response getAuthenticationToken() {
        return Response.ok("issued").build();
      }
    };
    resource.request = hk2Proxy(gatewayRequest);

    assertEquals(Response.Status.OK.getStatusCode(), resource.doPost().getStatus());
  }

  // Mimics the HK2 proxy Jersey injects into singletons: it delegates to the current request and
  // implements ProxyCtl, but it is not a ServletRequestWrapper.
  private static HttpServletRequest hk2Proxy(final HttpServletRequest current) {
    return (HttpServletRequest) Proxy.newProxyInstance(TokenResourceRequestProxyTest.class.getClassLoader(),
        new Class<?>[] {HttpServletRequest.class, ProxyCtl.class},
        (proxy, method, args) -> {
          if ("__make".equals(method.getName())) {
            return current;
          }
          try {
            return method.invoke(current, args);
          } catch (InvocationTargetException e) {
            throw e.getCause();
          }
        });
  }
}
