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

import io.grpc.Metadata;

/**
 * Adjusts call metadata in place before it is forwarded to the backend.
 * <p>
 * The two legs have separate credentials: the client's bearer token
 * authenticates the user to Knox and must not travel further, while the backend
 * gets Knox's own pre-shared token if one is configured. Knox-internal routing
 * metadata is dropped here too.
 */
@FunctionalInterface
public interface HeaderRewriter {

  /**
   * Rewrites the metadata that will be sent to the backend.
   *
   * @param headers the client's call metadata, modified in place
   */
  void rewrite(Metadata headers);
}
