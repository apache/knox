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
package org.apache.knox.gateway.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;

import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Knox reloads topologies from disk while running, but this listener does not go
 * through the webapp redeployment that refreshes the servlet filter chains. Its
 * cached authorization rules therefore have to be invalidated explicitly, or an
 * administrator's ACL edit silently never takes effect.
 */
public class AuthorizationInterceptorReloadTest {

  private static final String TOPOLOGY = "analytics";
  private static final String ROLE = "SPARKCONNECT";

  @Test
  public void picksUpAnAclChangeAfterReload() {
    final MutableTopologies topologies = new MutableTopologies();
    topologies.setAcl("alice;*;*");

    final AuthorizationInterceptor interceptor =
        new AuthorizationInterceptor(config(), services(topologies.asService()), ROLE);

    assertTrue("alice should be permitted by the initial ACL", permitted(interceptor, "alice"));
    assertEquals("bob should not be", Status.Code.PERMISSION_DENIED, denyCode(interceptor, "bob"));

    // An administrator edits the topology; the file monitor redeploys it.
    topologies.setAcl("alice,bob;*;*");

    // Without invalidation the interceptor keeps serving the ACL it first read.
    assertEquals("stale ACL should still be in effect before reload",
        Status.Code.PERMISSION_DENIED, denyCode(interceptor, "bob"));

    interceptor.invalidate();

    assertTrue("bob should be permitted once the change is picked up",
        permitted(interceptor, "bob"));
    assertTrue("alice should still be permitted", permitted(interceptor, "alice"));
  }

  @Test
  public void picksUpAnAclThatBecomesMoreRestrictive() {
    final MutableTopologies topologies = new MutableTopologies();
    topologies.setAcl("*;*;*");

    final AuthorizationInterceptor interceptor =
        new AuthorizationInterceptor(config(), services(topologies.asService()), ROLE);
    assertTrue(permitted(interceptor, "mallory"));

    // Revoking access matters more than granting it: this is the direction where
    // a stale cache leaves someone with access they were meant to lose.
    topologies.setAcl("alice;*;*");
    interceptor.invalidate();

    assertEquals(Status.Code.PERMISSION_DENIED, denyCode(interceptor, "mallory"));
    assertTrue(permitted(interceptor, "alice"));
  }

  private static boolean permitted(AuthorizationInterceptor interceptor, String user) {
    return outcome(interceptor, user) == null;
  }

  private static Status.Code denyCode(AuthorizationInterceptor interceptor, String user) {
    final Status status = outcome(interceptor, user);
    return status == null ? null : status.getCode();
  }

  /** Runs one call through the interceptor; returns null when it was allowed. */
  private static Status outcome(AuthorizationInterceptor interceptor, String user) {
    final GrpcCallContext callContext = new GrpcCallContext(
        "spark.connect.SparkConnectService/ExecutePlan", "knox", "10.0.0.5", System.nanoTime());
    callContext.setPrincipal(user);
    callContext.setTopology(TOPOLOGY);

    final RecordingServerCall call = new RecordingServerCall();
    final boolean[] proceeded = {false};
    final ServerCallHandler<byte[], byte[]> next = (c, h) -> {
      proceeded[0] = true;
      return new ServerCall.Listener<byte[]>() { };
    };

    Context.current().withValue(GrpcCallContext.KEY, callContext)
        .run(() -> interceptor.interceptCall(call, new Metadata(), next));

    return proceeded[0] ? null : call.closedWith;
  }

  private static GatewayConfig config() {
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getKnoxAdminUsers()).andReturn("").anyTimes();
    EasyMock.expect(config.getKnoxAdminGroups()).andReturn("").anyTimes();
    EasyMock.replay(config);
    return config;
  }

  private static GatewayServices services(TopologyService topologyService) {
    final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(services.<TopologyService>getService(ServiceType.TOPOLOGY_SERVICE))
        .andReturn(topologyService).anyTimes();
    EasyMock.replay(services);
    return services;
  }

  /** A single topology whose ACL parameter can be changed between calls. */
  private static final class MutableTopologies {

    private final Topology topology = new Topology();
    private final List<Topology> topologies = new ArrayList<>();

    MutableTopologies() {
      topology.setName(TOPOLOGY);
      topologies.add(topology);
    }

    /** Stands in for an administrator editing the file and the monitor redeploying it. */
    void setAcl(String acl) {
      topology.getProviders().clear();
      final Provider provider = new Provider();
      provider.setRole("authorization");
      provider.setName("AclsAuthz");
      provider.setEnabled(true);
      provider.getParams().put(ROLE + ".acl", acl);
      topology.addProvider(provider);
    }

    TopologyService asService() {
      final TopologyService service = EasyMock.createNiceMock(TopologyService.class);
      // Returns the same live list, so edits are visible to any later lookup.
      EasyMock.expect(service.getTopologies()).andReturn(topologies).anyTimes();
      EasyMock.replay(service);
      return service;
    }
  }

  /** Captures the status a rejected call was closed with. */
  private static final class RecordingServerCall extends ServerCall<byte[], byte[]> {

    private Status closedWith;

    @Override
    public void request(int numMessages) {
    }

    @Override
    public void sendHeaders(Metadata headers) {
    }

    @Override
    public void sendMessage(byte[] message) {
    }

    @Override
    public void close(Status status, Metadata trailers) {
      this.closedWith = status;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public Attributes getAttributes() {
      return Attributes.EMPTY;
    }

    @Override
    public MethodDescriptor<byte[], byte[]> getMethodDescriptor() {
      return MethodDescriptor.<byte[], byte[]>newBuilder()
          .setType(MethodDescriptor.MethodType.UNKNOWN)
          .setFullMethodName("spark.connect.SparkConnectService/ExecutePlan")
          .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
          .setResponseMarshaller(ByteArrayMarshaller.INSTANCE)
          .build();
    }
  }
}
