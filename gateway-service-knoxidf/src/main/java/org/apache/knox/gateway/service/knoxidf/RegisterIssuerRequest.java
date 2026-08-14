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
package org.apache.knox.gateway.service.knoxidf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for registering a trusted OIDC issuer via {@link TrustedOidcIssuersResource#registerIssuer(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterIssuerRequest {

  private String issuerUrl;
  private boolean dynamicJwks;
  private String clusterName;

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public void setIssuerUrl(String issuerUrl) {
    this.issuerUrl = issuerUrl;
  }

  public boolean isDynamicJwks() {
    return dynamicJwks;
  }

  public void setDynamicJwks(boolean dynamicJwks) {
    this.dynamicJwks = dynamicJwks;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }
}
