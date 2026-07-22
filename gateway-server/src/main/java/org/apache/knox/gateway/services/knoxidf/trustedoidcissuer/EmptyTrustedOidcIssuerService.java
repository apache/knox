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

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * No-op stub used when the KNOXIDF or KNOXIDF_ADMIN service role is not deployed.
 * Read methods return safe empty results; mutating methods throw
 * {@link UnsupportedOperationException}.
 */
public class EmptyTrustedOidcIssuerService implements TrustedOidcIssuerService {

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }

  @Override
  public boolean isTrusted(String issuerUrl) {
    return false;
  }

  @Override
  public boolean isDynamicJwks(String issuerUrl) {
    return false;
  }

  @Override
  public Optional<String> resolveJwksUri(String issuerUrl) {
    return Optional.empty();
  }

  @Override
  public void refreshJwksUri(String issuerUrl) {
  }

  @Override
  public void register(TrustedOidcIssuer issuer) {
    throw new UnsupportedOperationException("TrustedOidcIssuerService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public void deregister(String issuerUrl) {
    throw new UnsupportedOperationException("TrustedOidcIssuerService is not enabled; "
        + "deploy the KNOXIDF or KNOXIDF_ADMIN service role to activate it.");
  }

  @Override
  public List<TrustedOidcIssuer> list() {
    return Collections.emptyList();
  }
}
