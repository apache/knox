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
package org.apache.knox.gateway.sparkconnect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.apache.knox.gateway.GatewayTestConfig;
import org.apache.knox.gateway.grpc.GrpcCallContext;
import org.apache.knox.gateway.grpc.MessageInterceptor;

import com.google.protobuf.Message;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.apache.spark.connect.proto.AddArtifactsRequest;
import org.apache.spark.connect.proto.ConfigRequest;
import org.apache.spark.connect.proto.KeyValue;

import org.junit.Test;

/**
 * The message-level controls are the only Spark Connect settings that can change
 * on a running gateway; the rest are built into the bound server.
 * <p>
 * Each test obtains an interceptor <em>before</em> changing configuration and
 * asserts on that same instance afterwards. That is the property that matters:
 * handlers are registered once when the gRPC service is built, so a guard
 * captured at that moment could never be replaced, and the setting would be
 * silently restart-only.
 */
public class SparkConnectListenerConfigReloadTest {

  private static final String USER = "alice";

  @Test
  public void addArtifactsGatingTakesEffectWithoutRestart() {
    final GatewayTestConfig config = new GatewayTestConfig();
    config.setSparkConnectAddArtifactsMode(AddArtifactsGuard.MODE_ALLOW);
    final SparkConnectListener listener = started(config);

    final MessageInterceptor<Message> addArtifacts = listener.interceptorFor("AddArtifacts");
    intercept(addArtifacts, AddArtifactsRequest.getDefaultInstance());

    config.setSparkConnectAddArtifactsMode(AddArtifactsGuard.MODE_DENY);
    listener.onGatewayConfigChanged(config);

    assertDenied(addArtifacts, AddArtifactsRequest.getDefaultInstance());
  }

  @Test
  public void addArtifactsAllowListTakesEffectWithoutRestart() {
    final GatewayTestConfig config = new GatewayTestConfig();
    config.setSparkConnectAddArtifactsMode(AddArtifactsGuard.MODE_ALLOW_LISTED_USERS);
    config.setSparkConnectAddArtifactsAllowedUsers(Collections.emptyList());
    final SparkConnectListener listener = started(config);
    final MessageInterceptor<Message> addArtifacts = listener.interceptorFor("AddArtifacts");

    assertDenied(addArtifacts, AddArtifactsRequest.getDefaultInstance());

    config.setSparkConnectAddArtifactsAllowedUsers(Arrays.asList(USER, "bob"));
    listener.onGatewayConfigChanged(config);

    intercept(addArtifacts, AddArtifactsRequest.getDefaultInstance());
  }

  @Test
  public void reservedConfigPrefixTakesEffectWithoutRestart() {
    final GatewayTestConfig config = new GatewayTestConfig();
    final SparkConnectListener listener = started(config);
    final MessageInterceptor<Message> configRpc = listener.interceptorFor("Config");

    // 'acme.' is not reserved under the default 'knox.' prefix.
    intercept(configRpc, configSet("acme.principal", "root"));

    config.setSparkConnectReservedConfigPrefix("acme.");
    listener.onGatewayConfigChanged(config);

    assertDenied(configRpc, configSet("acme.principal", "root"));
  }

  @Test
  public void aRestartOnlyChangeDoesNotDisturbTheMessagePolicy() {
    final GatewayTestConfig config = new GatewayTestConfig();
    final SparkConnectListener listener = started(config);
    final MessageInterceptor<Message> configRpc = listener.interceptorFor("Config");

    // The port cannot be rebound on a running listener. Handling the change must
    // neither throw nor quietly drop the policy that is still in force.
    config.setSparkConnectPort(15099);
    listener.onGatewayConfigChanged(config);

    assertDenied(configRpc, configSet("knox.principal", "root"));
  }

  @Test
  public void identityAssertionIsUnaffectedByPolicyChanges() {
    final GatewayTestConfig config = new GatewayTestConfig();
    final SparkConnectListener listener = started(config);
    final MessageInterceptor<Message> configRpc = listener.interceptorFor("Config");

    config.setSparkConnectReservedConfigPrefix("acme.");
    listener.onGatewayConfigChanged(config);

    // A permitted call still gets its identity asserted; the guard swap must not
    // replace the interceptor's primary job.
    final ConfigRequest forwarded = (ConfigRequest) interceptAndReturn(
        configRpc, configSet("spark.sql.shuffle.partitions", "8"));
    assertEquals(USER, forwarded.getUserContext().getUserId());
  }

  /** Populates the listener's policy without binding a port. */
  private static SparkConnectListener started(GatewayTestConfig config) {
    final SparkConnectListener listener = new SparkConnectListener();
    listener.createSettings(config);
    return listener;
  }

  private static void intercept(MessageInterceptor<Message> interceptor, Message request) {
    interceptAndReturn(interceptor, request);
  }

  private static Message interceptAndReturn(MessageInterceptor<Message> interceptor,
                                            Message request) {
    final GrpcCallContext callContext =
        new GrpcCallContext("m", "authority", "127.0.0.1", System.nanoTime());
    callContext.setPrincipal(USER);
    final Message[] result = new Message[1];
    Context.current().withValue(GrpcCallContext.KEY, callContext)
        .run(() -> result[0] = interceptor.intercept(request));
    return result[0];
  }

  private static void assertDenied(MessageInterceptor<Message> interceptor, Message request) {
    try {
      intercept(interceptor, request);
      fail("Expected the request to be denied");
    } catch (StatusRuntimeException e) {
      assertEquals(Status.Code.PERMISSION_DENIED, e.getStatus().getCode());
    }
  }

  private static ConfigRequest configSet(String key, String value) {
    return ConfigRequest.newBuilder()
        .setOperation(ConfigRequest.Operation.newBuilder()
            .setSet(ConfigRequest.Set.newBuilder()
                .addPairs(KeyValue.newBuilder().setKey(key).setValue(value))))
        .build();
  }
}
