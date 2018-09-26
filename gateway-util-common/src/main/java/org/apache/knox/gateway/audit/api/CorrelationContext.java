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
package org.apache.knox.gateway.audit.api;

public interface CorrelationContext {

  /**
   * A unique value representing the current, active request.
   *
   * @return A unique value representing the current, active request.
   */
  String getRequestId();

  /**
   * A unique value representing the current, active request.
   * If the current request id value is different from the current parent request id value then
   * the current request id value is moved to the parent request id before it is replaced by the provided request id.
   * If the root request id is not set it will be set with the first non-null value of either the parent request id or the passed request id.
   *
   * @param requestId A unique value representing the current, active request.
   */
  void setRequestId( String requestId );

  /**
   * The parent request ID if this is a sub-request.
   *
   * @return The parent request ID.
   */
  String getParentRequestId();

  /**
   * Sets the parent request ID if this is a sub-request.
   *
   * @param parentRequestId The parent request ID.
   */
  void setParentRequestId( String parentRequestId );

  /**
   * The root request ID if this is a sub-request.
   *
   * @return The root request ID.
   */
  String getRootRequestId();

  /**
   * Sets the root request ID if this is a sub-request.
   *
   * @param rootRequestId The root request ID.
   */
  void setRootRequestId( String rootRequestId );

  /**
   * Would be used to indicate that the context can be cleaned and reused.  
   * This is only important if the service would like to maintain a pool of available "empty" context 
   * that can be reused to limit memory allocation and garbage collection.
   */
  void destroy();

}
