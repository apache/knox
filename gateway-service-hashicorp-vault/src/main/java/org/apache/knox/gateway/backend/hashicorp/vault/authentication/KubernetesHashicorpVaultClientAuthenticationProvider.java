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
package org.apache.knox.gateway.backend.hashicorp.vault.authentication;

import org.apache.knox.gateway.backend.hashicorp.vault.HashicorpVaultAliasService;
import org.apache.knox.gateway.services.security.AliasService;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.util.Map;

public class KubernetesHashicorpVaultClientAuthenticationProvider
    implements HashicorpVaultClientAuthenticationProvider {
  public static final String TYPE = "kubernetes";
  public static final String KUBERNETES_ROLE_KEY = AUTHENTICATION_CONFIG_PREFIX + "kubernetes.role";

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public ClientAuthentication newInstance(AliasService localAliasService,
                                          Map<String, String> properties) throws Exception {
    String role = properties.get(KUBERNETES_ROLE_KEY);
    KubernetesAuthenticationOptions kubernetesAuthenticationOptions =
        KubernetesAuthenticationOptions.builder().role(role).build();
    return new KubernetesAuthentication(kubernetesAuthenticationOptions,
        getRestOperations(properties));
  }

  private RestOperations getRestOperations(Map<String, String> properties) throws Exception {
    String vaultAddress = properties.get(HashicorpVaultAliasService.VAULT_ADDRESS_KEY);
    VaultEndpoint vaultEndpoint = VaultEndpoint.from(new URI(vaultAddress));
    VaultEndpointProvider vaultEndpointProvider = SimpleVaultEndpointProvider.of(vaultEndpoint);
    ClientOptions clientOptions = new ClientOptions();
    SslConfiguration sslConfiguration = SslConfiguration.unconfigured();
    ClientHttpRequestFactory clientHttpRequestFactory = ClientHttpRequestFactoryFactory.create(
        clientOptions, sslConfiguration);
    return VaultClients.createRestTemplate(vaultEndpointProvider, clientHttpRequestFactory);
  }
}
