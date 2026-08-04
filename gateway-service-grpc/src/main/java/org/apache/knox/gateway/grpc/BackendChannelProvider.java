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

import io.grpc.Channel;

/**
 * Supplies the backend channel for the call in flight.
 * <p>
 * Implementations read the backend the routing interceptor resolved into the
 * current {@link GrpcCallContext}, so the proxy handler itself never needs to
 * know how topologies map to backends.
 */
@FunctionalInterface
public interface BackendChannelProvider {

  /**
   * Returns the channel for the current call's backend.
   *
   * @return a channel to the backend
   * @throws io.grpc.StatusRuntimeException if no backend can be resolved
   */
  Channel getChannel();
}
