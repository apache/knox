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
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
 * A client picks its topology with a {@code knox-topology} connection parameter,
 * so selection is a client-supplied value and cannot be trusted on its own.
 * <p>
 * What makes it safe is that authorization runs after routing and is keyed on the
 * topology that was selected: asking for a topology is not the same as being
 * allowed to use it. These tests cover the cross-topology case specifically —
 * one identity, several topologies, different answers.
 */
public class TopologySelectionAuthorizationTest {

  private static final String ROLE = "SPARKCONNECT";
  private static final String IP = "10.0.0.5";

  @Test
  public void aUserMayReachOneTopologyAndNotAnother() {
    final Topologies topologies = new Topologies()
        .with("analytics", "alice;*;*")
        .with("etl", "bob;*;*");
    final AuthorizationInterceptor interceptor = interceptor(topologies);

    assertAllowed(interceptor, "analytics", "alice");
    // Alice can name 'etl' in her connection string; naming it is not reaching it.
    assertDenied(interceptor, "etl", "alice");

    assertAllowed(interceptor, "etl", "bob");
    assertDenied(interceptor, "analytics", "bob");
  }

  @Test
  public void topologySelectionCanBeAuthorizedByGroup() {
    final Topologies topologies = new Topologies()
        .with("analytics", "*;analysts;*")
        .with("etl", "*;engineers;*");
    final AuthorizationInterceptor interceptor = interceptor(topologies);

    assertAllowed(interceptor, "analytics", "alice", "analysts");
    assertDenied(interceptor, "etl", "alice", "analysts");

    // Membership of both groups reaches both clusters.
    assertAllowed(interceptor, "analytics", "carol", "analysts", "engineers");
    assertAllowed(interceptor, "etl", "carol", "analysts", "engineers");
  }

  @Test
  public void eachTopologyIsEvaluatedAgainstItsOwnRules() {
    // The per-topology authorizer cache must not let one topology's decision
    // leak into another's.
    final Topologies topologies = new Topologies()
        .with("open", "*;*;*")
        .with("restricted", "alice;*;*");
    final AuthorizationInterceptor interceptor = interceptor(topologies);

    assertAllowed(interceptor, "open", "mallory");
    assertDenied(interceptor, "restricted", "mallory");
    // Re-check the first, in case evaluating the second disturbed it.
    assertAllowed(interceptor, "open", "mallory");
  }

  @Test
  public void aTopologyWithNoAclIsReachableByAnyAuthenticatedUser() {
    // Worth pinning because it is the permissive direction: declaring no ACL for
    // the role does not restrict selection, it leaves the topology open to anyone
    // who can authenticate. Restricting selection means setting an ACL.
    final Topologies topologies = new Topologies().withNoAclProvider("wide-open");
    final AuthorizationInterceptor interceptor = interceptor(topologies);

    assertAllowed(interceptor, "wide-open", "mallory");
  }

  @Test
  public void aDisabledAclProviderDoesNotRestrictSelection() {
    final Topologies topologies = new Topologies().withDisabled("analytics", "alice;*;*");
    final AuthorizationInterceptor interceptor = interceptor(topologies);

    assertAllowed(interceptor, "analytics", "mallory");
  }

  private static void assertAllowed(AuthorizationInterceptor interceptor,
                                    String topology, String user, String... groups) {
    assertNull("expected " + user + " to be allowed into " + topology,
        outcome(interceptor, topology, user, groups));
  }

  private static void assertDenied(AuthorizationInterceptor interceptor,
                                   String topology, String user, String... groups) {
    final Status status = outcome(interceptor, topology, user, groups);
    assertEquals("expected " + user + " to be denied " + topology,
        Status.Code.PERMISSION_DENIED, status == null ? null : status.getCode());
  }

  /** Runs one call; returns null when it was allowed through. */
  private static Status outcome(AuthorizationInterceptor interceptor,
                                String topology, String user, String... groups) {
    final GrpcCallContext callContext = new GrpcCallContext(
        "spark.connect.SparkConnectService/ExecutePlan", "knox", IP, System.nanoTime());
    callContext.setPrincipal(user);
    callContext.setGroups(new HashSet<>(Arrays.asList(groups)));
    // Set by the routing interceptor from the client's knox-topology parameter.
    callContext.setTopology(topology);

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

  private static AuthorizationInterceptor interceptor(Topologies topologies) {
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getKnoxAdminUsers()).andReturn("").anyTimes();
    EasyMock.expect(config.getKnoxAdminGroups()).andReturn("").anyTimes();
    EasyMock.replay(config);

    final TopologyService topologyService = EasyMock.createNiceMock(TopologyService.class);
    EasyMock.expect(topologyService.getTopologies()).andReturn(topologies.all()).anyTimes();
    EasyMock.replay(topologyService);

    final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(services.<TopologyService>getService(ServiceType.TOPOLOGY_SERVICE))
        .andReturn(topologyService).anyTimes();
    EasyMock.replay(services);

    return new AuthorizationInterceptor(config, services, ROLE);
  }

  /** Builds a set of topologies with differing ACLs. */
  private static final class Topologies {

    private final List<Topology> declared = new ArrayList<>();

    Topologies with(String name, String acl) {
      declared.add(topology(name, acl, true));
      return this;
    }

    Topologies withDisabled(String name, String acl) {
      declared.add(topology(name, acl, false));
      return this;
    }

    Topologies withNoAclProvider(String name) {
      final Topology topology = new Topology();
      topology.setName(name);
      declared.add(topology);
      return this;
    }

    private static Topology topology(String name, String acl, boolean enabled) {
      final Topology topology = new Topology();
      topology.setName(name);
      final Provider provider = new Provider();
      provider.setRole("authorization");
      provider.setName("AclsAuthz");
      provider.setEnabled(enabled);
      provider.getParams().put(ROLE + ".acl", acl);
      topology.addProvider(provider);
      return topology;
    }

    List<Topology> all() {
      return Collections.unmodifiableList(declared);
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
