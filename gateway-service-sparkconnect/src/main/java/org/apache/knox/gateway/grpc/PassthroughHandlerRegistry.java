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

import java.util.Locale;
import java.util.Set;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;

/**
 * Handles calls to methods that have no typed handler, relaying them as opaque
 * bytes.
 * <p>
 * This exists so that proto skew degrades gracefully. When a client calls an RPC
 * this build has no generated classes for — an addition in a newer Spark line,
 * typically — the call is still authenticated, authorized, routed and audited;
 * only the message-body handling is skipped, because there is nothing to inspect
 * with. Without it, such a call would fail outright at the gateway even though
 * the backend could serve it.
 * <p>
 * The trade-off is explicit: no identity assertion happens on this path, since
 * rewriting {@code user_context} requires parsing the message. Passthrough is
 * therefore restricted to a configured set of proto services, and default-denies
 * everything else — a gateway that forwarded arbitrary unknown services without
 * being asked to would be a very different, and much weaker, security posture.
 */
public class PassthroughHandlerRegistry extends HandlerRegistry {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final Set<String> allowedServices;
  private final ServerCallHandler<byte[], byte[]> handler;

  /**
   * @param allowedServices fully qualified proto service names whose unknown
   *        methods may be relayed, e.g. {@code spark.connect.SparkConnectService}
   * @param handler the proxy handler to relay with
   */
  public PassthroughHandlerRegistry(Set<String> allowedServices, ServerCallHandler<byte[], byte[]> handler) {
    this.allowedServices = allowedServices;
    this.handler = handler;
  }

  @Override
  public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
    final String serviceName = MethodDescriptor.extractFullServiceName(methodName);
    if (serviceName == null || !allowedServices.contains(serviceName)) {
      // Returning null makes grpc answer UNIMPLEMENTED, which is also what a real
      // server says about a method it does not have — so this reveals nothing
      // about what the gateway is fronting.
      return null;
    }

    LOG.debugLog(String.format(Locale.ROOT,
        "Relaying %s as opaque bytes; no typed handler is registered for it", methodName));

    final MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
        // UNKNOWN keeps grpc from assuming a message count in either direction,
        // so unary and streaming methods alike relay correctly.
        .setType(MethodDescriptor.MethodType.UNKNOWN)
        .setFullMethodName(methodName)
        .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
        .setResponseMarshaller(ByteArrayMarshaller.INSTANCE)
        .build();
    return ServerMethodDefinition.create(descriptor, handler);
  }
}
