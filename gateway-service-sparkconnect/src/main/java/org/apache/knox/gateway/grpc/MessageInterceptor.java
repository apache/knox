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

import io.grpc.StatusRuntimeException;

/**
 * Inspects and optionally rewrites each request message on its way to the
 * backend.
 * <p>
 * This is the seam between the generic gRPC core and a protocol-aware plugin.
 * The core never parses message bodies; everything that needs to — identity
 * assertion, per-RPC gating, reserved-key protection — is expressed here. A
 * generic byte-level proxy simply uses {@link #PASSTHROUGH}.
 *
 * @param <T> the request message type
 */
@FunctionalInterface
public interface MessageInterceptor<T> {

  /**
   * A message interceptor that forwards every message unchanged. This is what
   * makes the byte-level path a pure pipe.
   */
  MessageInterceptor<Object> PASSTHROUGH = message -> message;

  /**
   * Returns the message to forward to the backend, which may be the argument
   * itself or a rewritten copy.
   * <p>
   * Throwing {@link StatusRuntimeException} rejects the call with that status;
   * the proxy closes the client call and cancels the backend call. This is how
   * per-RPC gating denies a request without the backend ever seeing it.
   *
   * @param message the message received from the client
   * @return the message to send to the backend
   * @throws StatusRuntimeException to reject the call
   */
  T intercept(T message);

  /**
   * Returns the passthrough interceptor, typed for the caller's message type.
   *
   * @param <T> the request message type
   * @return an interceptor that forwards every message unchanged
   */
  @SuppressWarnings("unchecked")
  static <T> MessageInterceptor<T> passthrough() {
    return (MessageInterceptor<T>) PASSTHROUGH;
  }
}
