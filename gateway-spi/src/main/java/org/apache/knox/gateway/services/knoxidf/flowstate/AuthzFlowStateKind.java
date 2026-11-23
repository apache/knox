/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.knoxidf.flowstate;

/**
 * The kinds of in-flight KnoxIDF authorization flow state. Each kind has its own id space.
 */
public enum AuthzFlowStateKind {
  /** A validated authorize request waiting for user consent or for the federated OP callback. */
  AUTHORIZE_REQUEST("authz_request"),
  /** The federated OPs offered on the KnoxSSO login page for a federated login session. */
  FEDERATED_OP_OPTIONS("fed_op_options"),
  /** The federated OP the user picked for a federated login session and the nonce sent to it. */
  FEDERATED_OP_SELECTION("fed_op_selection");

  // Persisted in the KIND column; never change an existing value.
  private final String id;

  AuthzFlowStateKind(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
