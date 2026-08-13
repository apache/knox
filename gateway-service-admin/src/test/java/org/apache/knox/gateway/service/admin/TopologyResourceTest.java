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
package org.apache.knox.gateway.service.admin;

import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TopologyResourceTest {

  private final String X_Forwarded = "X-Forwarded-";
  private final String X_Forwarded_Context = X_Forwarded + "Context";
  private final String X_Forwarded_Proto = X_Forwarded + "Proto";
  private final String X_Forwarded_Host = X_Forwarded + "Host";
  private final String X_Forwarded_Port = X_Forwarded + "Port";
  private final String X_Forwarded_Server = X_Forwarded + "Server";

  private String reqProto = "http";
  private String reqServer = "req-server";
  private String reqPort = "9001";
  private String gatewayPath = "gateway-path";
  private String reqContext = "/" + gatewayPath + "/a-topology";
  private String proto = "proto";
  private String port = "1337";
  private String server = "my-server";
  private String host = server + ":" + port;
  private String startContext = "/mycontext";
  private String fullContext = startContext + reqContext;
  private String pathInfo = "/api/version";
  private String topologyName = "topology-name";
  private String expectedBase = proto + "://" + server + ":" + port + "/mycontext/" + gatewayPath;
  private String expectedURI = expectedBase + "/" + topologyName;
  private String expectedHref = expectedBase + "/a-topology" + pathInfo + "/" + topologyName;

  @Test
  public void testTopologyURLMethods(){
//     Note: had to change method signature due to these tests. Changed methods to public and added
//    HttpServletRequest argument.
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Host, host);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Server, server);

    Topology t = EasyMock.createNiceMock( Topology.class );
    EasyMock.expect( t.getName() ).andReturn( topologyName ).anyTimes();

    GatewayConfig conf = EasyMock.createNiceMock( GatewayConfig.class );
    EasyMock.expect( conf.getGatewayPath() ).andReturn( gatewayPath ).anyTimes();
    EasyMock.replay( request, t, conf );

    TopologiesResource res = new TopologiesResource();
    String href = res.buildHref(t, request);
    String uri = res.buildURI(t, conf, request);

    assertThat( uri, containsString( proto ) );
    assertThat( uri, containsString( host ) );
    assertThat( uri, containsString( server ) );
    assertThat( uri, containsString( port ) );
    assertThat( uri, containsString( topologyName ) );
    assert( uri.equals(expectedURI) );

    assertThat( href, containsString( proto ) );
    assertThat( href, containsString( host ) );
    assertThat( href, containsString( server ) );
    assertThat( href, containsString( port ) );
    assertThat( href, containsString( fullContext ) );
    assert( href.equals(expectedHref) );

//    Test 2 - No Protocol Header
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Host, host);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Server, server);
    EasyMock.replay(request);

    String test2URI = expectedURI.replace(proto, reqProto);
    String test2href = expectedHref.replace(proto, reqProto);
    assert(res.buildURI(t, conf, request).equals(test2URI));
    assert(res.buildHref(t, request).equals(test2href));

//    Test 3 - No port in host Header
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Host, server);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Server, server);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    EasyMock.replay(request);

    assert(res.buildURI(t, conf, request).equals(expectedURI));
    assert(res.buildHref(t, request).equals(expectedHref));


//    Test 4 - server & no host Header
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Server, server);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    EasyMock.replay(request);

    assert(res.buildURI(t, conf, request).equals(expectedURI));
    assert(res.buildHref(t, request).equals(expectedHref));


    //    Test 5 - no server & no host Header
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    EasyMock.replay(request);

    String test5URI = expectedURI.replace(server, reqServer);
    String test5href = expectedHref.replace(server, reqServer);
    assertEquals(res.buildURI(t, conf, request), test5URI);
    assertEquals(res.buildHref(t, request), test5href);


    //    Test 6 - no port, no server & no host Header
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Context, fullContext);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    EasyMock.replay(request);

    String test6URI = expectedURI.replace(server, reqServer).replace(port, reqPort);
    String test6href = expectedHref.replace(server, reqServer).replace(port, reqPort);
    assertEquals(res.buildURI(t, conf, request), (test6URI));
    assertEquals(res.buildHref(t, request), (test6href));

    //    Test 7 - No Context
    EasyMock.reset( request );
    setDefaultExpectations(request);
    setMockRequestHeader(request, X_Forwarded_Host, host);
    setMockRequestHeader(request, X_Forwarded_Port, port);
    setMockRequestHeader(request, X_Forwarded_Proto, proto);
    setMockRequestHeader(request, X_Forwarded_Server, server);
    EasyMock.replay(request);

    String test7URI = expectedURI.replace(startContext, "");
    String test7href = expectedHref.replace(startContext, "");
    assertEquals(res.buildURI(t, conf, request), test7URI);
    assertEquals(res.buildHref(t, request), test7href);

  }

  @Test
  public void testResourceNameValidation() {
    assertTrue(TopologiesResource.isValidResourceName("foo"));
    assertTrue(TopologiesResource.isValidResourceName("foo.json"));
    assertTrue(TopologiesResource.isValidResourceName("my-provider_1"));
    assertTrue(TopologiesResource.isValidResourceName("a.b.c"));

    assertFalse(TopologiesResource.isValidResourceName("../../etc/passwd"));
    assertFalse(TopologiesResource.isValidResourceName(".."));
    assertFalse(TopologiesResource.isValidResourceName("foo/bar"));
    assertFalse(TopologiesResource.isValidResourceName("a/../../b"));
    assertFalse(TopologiesResource.isValidResourceName("foo..bar"));

    assertFalse(TopologiesResource.isValidResourceName("%2f"));
    assertFalse(TopologiesResource.isValidResourceName("%252f"));

    assertFalse(TopologiesResource.isValidResourceName(null));
    assertFalse(TopologiesResource.isValidResourceName(""));
    assertFalse(TopologiesResource.isValidResourceName("a".repeat(101)));
    assertTrue(TopologiesResource.isValidResourceName("a".repeat(100)));
  }

  private void setDefaultExpectations(HttpServletRequest request){
    EasyMock.expect( request.getPathInfo() ).andReturn( pathInfo ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( reqContext ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( reqServer ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( Integer.parseInt( reqPort ) ).anyTimes();
    EasyMock.expect( request.getProtocol() ).andReturn(reqProto).anyTimes();
  }

  private void setMockRequestHeader(HttpServletRequest request, String header, String expected){
    EasyMock.expect( request.getHeader( header ) ).andReturn( expected ).anyTimes();
  }

  @Test
  public void testUploadTopologyRefusesReadOnlyOverride() throws Exception {
    TopologyService ts = EasyMock.createMock(TopologyService.class);

    GatewayServices gs = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gs.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(ts).anyTimes();

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getReadOnlyOverrideTopologyNames())
        .andReturn(List.of("manager")).anyTimes();

    HttpServletRequest request = mockRequest(gs, config);

    EasyMock.replay(ts, gs, config, request);

    TopologiesResource res = new TopologiesResource();
    setRequestField(res, request);

    try {
      res.uploadTopology("manager", new org.apache.knox.gateway.service.admin.beans.Topology());
      fail("Expected WebApplicationException for read-only override topology");
    } catch (WebApplicationException e) {
      assertEquals(Response.Status.FORBIDDEN.getStatusCode(), e.getResponse().getStatus());
    }

    EasyMock.verify(ts);
  }

  @Test
  public void testUploadTopologyAllowedWhenNotReadOnly() throws Exception {
    TopologyService ts = EasyMock.createMock(TopologyService.class);
    EasyMock.expect(ts.getTopologies()).andReturn(Collections.emptyList()).anyTimes();
    ts.deployTopology(EasyMock.anyObject(Topology.class));
    EasyMock.expectLastCall().once();

    GatewayServices gs = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gs.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(ts).anyTimes();

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getReadOnlyOverrideTopologyNames())
        .andReturn(Collections.emptyList()).anyTimes();

    HttpServletRequest request = mockRequest(gs, config);

    EasyMock.replay(ts, gs, config, request);

    TopologiesResource res = new TopologiesResource();
    setRequestField(res, request);

    res.uploadTopology("sandbox", new org.apache.knox.gateway.service.admin.beans.Topology());

    EasyMock.verify(ts);
  }

  private HttpServletRequest mockRequest(GatewayServices gs, GatewayConfig config) {
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE))
        .andReturn(gs).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE))
        .andReturn(config).anyTimes();
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getServletContext()).andReturn(context).anyTimes();
    EasyMock.replay(context);
    return request;
  }

  private void setRequestField(TopologiesResource res, HttpServletRequest request) throws Exception {
    Field f = TopologiesResource.class.getDeclaredField("request");
    f.setAccessible(true);
    f.set(res, request);
  }

}
