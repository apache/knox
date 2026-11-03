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

import java.util.List;

import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Wraps a call handler in an explicitly ordered interceptor chain.
 * <p>
 * Order is a correctness property here, not a preference: routing must have
 * chosen a topology before ACLs for that topology can be evaluated, and
 * authentication must have established a principal before either. Rather than
 * depend on the registration-order semantics of a builder, the chain is composed
 * directly so the ordering is visible at the call site and cannot drift.
 */
public final class InterceptorChain {

  private InterceptorChain() {
  }

  /**
   * Returns a handler that applies the interceptors in list order, so the first
   * element sees the call first and closes it last.
   *
   * @param handler the innermost handler
   * @param interceptors the interceptors, outermost first
   * @param <ReqT> the request message type
   * @param <RespT> the response message type
   * @return the wrapped handler
   */
  public static <ReqT, RespT> ServerCallHandler<ReqT, RespT> intercept(
      ServerCallHandler<ReqT, RespT> handler, List<ServerInterceptor> interceptors) {
    ServerCallHandler<ReqT, RespT> result = handler;
    for (int i = interceptors.size() - 1; i >= 0; i--) {
      final ServerInterceptor interceptor = interceptors.get(i);
      final ServerCallHandler<ReqT, RespT> next = result;
      result = (call, headers) -> interceptor.interceptCall(call, headers, next);
    }
    return result;
  }
}
