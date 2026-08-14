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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import java.time.Instant;

public final class TrustedOidcIssuer {

  private final String issuerUrl;
  private final boolean dynamicJwks;
  private final String clusterName;
  private final Instant registeredAt;
  private final String registeredBy;

  public TrustedOidcIssuer(String issuerUrl, boolean dynamicJwks, String clusterName,
      Instant registeredAt, String registeredBy) {
    this.issuerUrl = issuerUrl;
    this.dynamicJwks = dynamicJwks;
    this.clusterName = clusterName;
    this.registeredAt = registeredAt;
    this.registeredBy = registeredBy;
  }

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public boolean isDynamicJwks() {
    return dynamicJwks;
  }

  /**
   * @return the cluster name this issuer belongs to, or null if not cluster-scoped
   */
  public String getClusterName() {
    return clusterName;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  /**
   * @return the identity that registered this issuer, or null if not recorded
   */
  public String getRegisteredBy() {
    return registeredBy;
  }
}
