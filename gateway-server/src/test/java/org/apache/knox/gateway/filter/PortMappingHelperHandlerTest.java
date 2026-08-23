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
package org.apache.knox.gateway.filter;

import static org.apache.knox.gateway.filter.AbstractGatewayFilter.DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class PortMappingHelperHandlerTest {

  private static Request mockRequest(String uri, int localPort) {
    ConnectionMetaData meta = EasyMock.createNiceMock(ConnectionMetaData.class);
    EasyMock.expect(meta.getLocalSocketAddress())
        .andReturn(new InetSocketAddress("localhost", localPort)).anyTimes();

    Request request = EasyMock.createNiceMock(Request.class);
    EasyMock.expect(request.getHttpURI())
        .andReturn(HttpURI.from(uri)).anyTimes();
    EasyMock.expect(request.getConnectionMetaData())
        .andReturn(meta).anyTimes();

    EasyMock.replay(meta, request);
    return request;
  }

  private static Request dispatchAndCapture(PortMappingHelperHandler handler,
                                             Request request) throws Exception {
    AtomicReference<Request> captured = new AtomicReference<>();
    handler.setHandler(new Handler.Abstract() {
      @Override
      public boolean handle(Request req, Response res, Callback cb) {
        captured.set(req);
        cb.succeeded();
        return true;
      }
    });
    handler.start();
    try {
      handler.handle(request, null, Callback.NOOP);
    } finally {
      handler.stop();
    }
    return captured.get();
  }

  // -----------------------------------------------------------------------
  // Port-mapping tests
  // -----------------------------------------------------------------------

  @Test
  public void testPortMapping_rewritesPathWhenContextMissing() throws Exception {
    Map<String, Integer> mappings = new ConcurrentHashMap<>();
    mappings.put("eerie", 9999);

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(mappings).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    Request request = mockRequest("http://localhost:9999/webhdfs", 9999);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/gateway/eerie/webhdfs", seen.getHttpURI().getPath());
  }

  @Test
  public void testPortMapping_rewritesPathWhenURIHasNoPort() throws Exception {
    Map<String, Integer> mappings = new ConcurrentHashMap<>();
    mappings.put("eerie", 9999);

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(mappings).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    // URI has no explicit port (Host: localhost with no ":port") — socket port still 9999
    Request request = mockRequest("http://localhost/webhdfs", 9999);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/gateway/eerie/webhdfs", seen.getHttpURI().getPath());
    // port slot in the rewritten URI must remain absent (-1), not mangled
    assertEquals(-1, seen.getHttpURI().getPort());
  }

  @Test
  public void testPortMapping_doesNotDoubleRewriteWhenContextPresent() throws Exception {
    Map<String, Integer> mappings = new ConcurrentHashMap<>();
    mappings.put("eerie", 9999);

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(mappings).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    // URI already contains /gateway/eerie
    Request request = mockRequest("http://localhost:9999/gateway/eerie/webhdfs", 9999);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/gateway/eerie/webhdfs", seen.getHttpURI().getPath());
  }

  @Test
  public void testPortMapping_disabled_passesThroughUnchanged() throws Exception {
    Map<String, Integer> mappings = new ConcurrentHashMap<>();
    mappings.put("eerie", 9999);

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(mappings).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    Request request = mockRequest("http://localhost:9999/webhdfs", 9999);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/webhdfs", seen.getHttpURI().getPath());
  }

  // -----------------------------------------------------------------------
  // Default topology tests
  // -----------------------------------------------------------------------

  @Test
  public void testDefaultTopology_rewritesPathWhenNotOnGatewayPath() throws Exception {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(Map.of()).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.expect(config.getDefaultTopologyName()).andReturn("eerie").anyTimes();
    EasyMock.expect(config.getDefaultAppRedirectPath()).andReturn("/gateway/eerie").anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    // request arrives on the standard port with no gateway prefix
    Request request = mockRequest("http://localhost:8443/webhdfs", 8443);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/gateway/eerie/webhdfs", seen.getHttpURI().getPath());
  }

  @Test
  public void testDefaultTopology_setsForwardAttribute() throws Exception {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(Map.of()).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.expect(config.getDefaultTopologyName()).andReturn("eerie").anyTimes();
    EasyMock.expect(config.getDefaultAppRedirectPath()).andReturn("/gateway/eerie").anyTimes();
    EasyMock.replay(config);

    // setAttribute on Request.Wrapper delegates to the wrapped object, so we
    // verify directly that the handler calls setAttribute with the right args.
    ConnectionMetaData meta = EasyMock.createNiceMock(ConnectionMetaData.class);
    EasyMock.expect(meta.getLocalSocketAddress())
        .andReturn(new InetSocketAddress("localhost", 8443)).anyTimes();

    Request request = EasyMock.createNiceMock(Request.class);
    EasyMock.expect(request.getHttpURI())
        .andReturn(HttpURI.from("http://localhost:8443/webhdfs")).anyTimes();
    EasyMock.expect(request.getConnectionMetaData()).andReturn(meta).anyTimes();
    // expect the attribute to be set exactly once with the correct value
    EasyMock.expect(request.setAttribute(DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME, "true"))
        .andReturn(null).once();

    EasyMock.replay(meta, request);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    handler.setHandler(new Handler.Abstract() {
      @Override
      public boolean handle(Request req, Response res, Callback cb) {
        cb.succeeded();
        return true;
      }
    });
    handler.start();
    try {
      handler.handle(request, null, Callback.NOOP);
    } finally {
      handler.stop();
    }

    EasyMock.verify(request);
  }

  @Test
  public void testDefaultTopology_alreadyOnGatewayPath_passesThroughWithoutAttribute()
      throws Exception {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(Map.of()).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.expect(config.getDefaultTopologyName()).andReturn("eerie").anyTimes();
    EasyMock.expect(config.getDefaultAppRedirectPath()).andReturn("/gateway/eerie").anyTimes();
    EasyMock.replay(config);

    AtomicReference<Object> attrValue = new AtomicReference<>();
    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    handler.setHandler(new Handler.Abstract() {
      @Override
      public boolean handle(Request req, Response res, Callback cb) {
        attrValue.set(req.getAttribute(DEFAULT_TOPOLOGY_FORWARD_ATTRIBUTE_NAME));
        cb.succeeded();
        return true;
      }
    });
    handler.start();
    try {
      handler.handle(
          mockRequest("http://localhost:8443/gateway/eerie/webhdfs", 8443), null, Callback.NOOP);
    } finally {
      handler.stop();
    }

    assertNull("forward attribute must NOT be set when path already starts with /gateway",
        attrValue.get());
  }

  // -----------------------------------------------------------------------
  // Fallthrough (no feature active)
  // -----------------------------------------------------------------------

  @Test
  public void testNoFeature_passesThroughUnchanged() throws Exception {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.isGatewayPortMappingEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(config.getGatewayPortMappings()).andReturn(Map.of()).anyTimes();
    EasyMock.expect(config.getGatewayPath()).andReturn("gateway").anyTimes();
    EasyMock.expect(config.getGatewayPort()).andReturn(8443).anyTimes();
    EasyMock.replay(config);

    PortMappingHelperHandler handler = new PortMappingHelperHandler(config);
    Request request = mockRequest("http://localhost:8443/gateway/eerie/webhdfs", 8443);

    Request seen = dispatchAndCapture(handler, request);
    assertEquals("/gateway/eerie/webhdfs", seen.getHttpURI().getPath());
  }
}
