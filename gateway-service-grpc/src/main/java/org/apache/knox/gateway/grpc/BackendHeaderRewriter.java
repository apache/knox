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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import io.grpc.Metadata;

/**
 * Replaces the client's credentials with Knox's own on the backend leg.
 * <p>
 * The client's bearer token proves the user's identity to Knox and has no
 * meaning beyond it, so it is removed rather than forwarded. In its place, if a
 * pre-shared backend token is configured, Knox presents that. Besides
 * authenticating the gateway to the backend, it closes the hole where a client
 * with network reachability to the backend port could simply bypass the gateway
 * altogether — network restrictions should prevent that too, but a credential
 * the client does not hold makes it structural rather than topological.
 * <p>
 * Knox-internal routing metadata is dropped for the same reason: it was
 * addressed to the gateway, and the backend has no use for it.
 */
public class BackendHeaderRewriter implements HeaderRewriter {

  private static final GrpcGatewayMessages LOG = MessagesFactory.get(GrpcGatewayMessages.class);

  private final String backendAuthorization;
  private final Metadata.Key<String> topologyKey;

  /**
   * @param aliasService used to resolve the backend token; may be null when no
   *        alias is configured
   * @param backendTokenAlias the alias holding the backend's pre-shared token, or
   *        null if the backend requires no token
   */
  public BackendHeaderRewriter(AliasService aliasService, String backendTokenAlias,
                               Metadata.Key<String> topologyKey) {
    this.backendAuthorization = resolveBackendToken(aliasService, backendTokenAlias);
    this.topologyKey = topologyKey;
  }

  private static String resolveBackendToken(AliasService aliasService, String alias) {
    if (aliasService == null || alias == null || alias.trim().isEmpty()) {
      return null;
    }
    try {
      final char[] token = aliasService.getPasswordFromAliasForGateway(alias);
      if (token == null || token.length == 0) {
        LOG.missingBackendTokenAlias(alias);
        return null;
      }
      return GrpcMetadataKeys.BEARER_PREFIX + new String(token);
    } catch (AliasServiceException e) {
      LOG.missingBackendTokenAlias(alias);
      return null;
    }
  }

  @Override
  public void rewrite(Metadata headers) {
    headers.removeAll(GrpcMetadataKeys.AUTHORIZATION);
    headers.removeAll(topologyKey);
    if (backendAuthorization != null) {
      headers.put(GrpcMetadataKeys.AUTHORIZATION, backendAuthorization);
    }
  }
}
