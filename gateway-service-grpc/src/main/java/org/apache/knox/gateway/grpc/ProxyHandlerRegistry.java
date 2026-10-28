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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.HandlerRegistry;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;

/**
 * Dispatches every call, for any method of a permitted proto service, to the
 * byte-level relay.
 * <p>
 * The gateway registers no generated service and knows no message types. A
 * method is identified by its name alone — which is all gRPC puts on the wire —
 * and its messages are relayed as opaque bytes. Whatever inspection a call needs
 * comes from the {@link MessageInterceptorFactory}, which works on those bytes
 * by field number rather than by schema.
 * <p>
 * Two consequences worth stating. An RPC added by a newer version of the
 * protocol is proxied like any other, because nothing here enumerates methods.
 * And proto services not named in the permitted set are answered
 * {@code UNIMPLEMENTED} — the same answer a real server gives for a method it
 * does not have, so this reveals nothing about what the gateway fronts.
 */
public class ProxyHandlerRegistry extends HandlerRegistry {

  private final Set<String> proxiedServices;
  private final MessageInterceptorFactory interceptors;
  private final HandlerFactory handlers;
  /** Handlers are stateless per method; build each once rather than per call. */
  private final Map<String, ServerMethodDefinition<byte[], byte[]>> methods = new ConcurrentHashMap<>();

  /**
   * Builds a fully wired handler for one method: the relay itself, plus the
   * authentication, routing, authorization and audit chain around it. The
   * listener supplies this so the registry needs to know nothing about backends
   * or interceptor ordering.
   */
  @FunctionalInterface
  public interface HandlerFactory {
    ServerCallHandler<byte[], byte[]> create(MessageInterceptor<byte[]> messageInterceptor);
  }

  /**
   * @param proxiedServices fully qualified proto service names this listener fronts
   * @param interceptors chooses the per-method request handling
   * @param handlers builds the relay and its interceptor chain
   */
  public ProxyHandlerRegistry(Set<String> proxiedServices,
                              MessageInterceptorFactory interceptors,
                              HandlerFactory handlers) {
    this.proxiedServices = proxiedServices;
    this.interceptors = interceptors;
    this.handlers = handlers;
  }

  @Override
  public ServerMethodDefinition<?, ?> lookupMethod(String methodName, String authority) {
    final String serviceName = MethodDescriptor.extractFullServiceName(methodName);
    if (serviceName == null || !proxiedServices.contains(serviceName)) {
      return null;
    }
    return methods.computeIfAbsent(methodName, this::createMethod);
  }

  private ServerMethodDefinition<byte[], byte[]> createMethod(String methodName) {
    final MethodDescriptor<byte[], byte[]> descriptor = MethodDescriptor.<byte[], byte[]>newBuilder()
        // UNKNOWN keeps grpc from assuming a message count in either direction, so
        // unary and streaming methods alike relay correctly through one handler.
        .setType(MethodDescriptor.MethodType.UNKNOWN)
        .setFullMethodName(methodName)
        .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
        .setResponseMarshaller(ByteArrayMarshaller.INSTANCE)
        .build();
    return ServerMethodDefinition.create(descriptor,
        handlers.create(interceptors.forMethod(methodName)));
  }
}
