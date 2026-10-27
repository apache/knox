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

/**
 * Chooses what, if anything, to do to the request messages of a given RPC.
 * <p>
 * This is the entire seam between the protocol-agnostic gateway and a
 * protocol-aware plugin. The gateway relays opaque bytes and knows only method
 * names; a plugin returns an interceptor for the methods it cares about and
 * {@link MessageInterceptor#passthrough()} for the rest.
 * <p>
 * Interceptors are built once per method, so anything a deployment can change
 * while running must be read when a message is intercepted rather than captured
 * here.
 */
@FunctionalInterface
public interface MessageInterceptorFactory {

  /**
   * @param fullMethodName the gRPC method name, {@code pkg.Service/Method}
   * @return the interceptor for that method's requests; never null
   */
  MessageInterceptor<byte[]> forMethod(String fullMethodName);
}
