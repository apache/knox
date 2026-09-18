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

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * The RFC 8414 OAuth 2.0 Authorization Server Metadata document served at the topology root
 * ({@code /gateway/<topology>/.well-known/oauth-authorization-server}) -- the well-known location
 * OAuth-only clients probe relative to the base URL, and Knox's closest practical approximation of
 * the RFC 8414 canonical placement (the gateway servlet is mounted under {@code /gateway/<topology>/},
 * so true host-root placement is not possible).
 *
 * <p>This differs from {@link OAuthServerMetadataResource} only in its {@code @Path}: it is a second
 * root resource that inherits that class's {@code @Context} injection, {@code @PostConstruct init()},
 * and {@code @GET getMetadata()} unchanged, so the two placements cannot drift. It requires the
 * {@code .well-known/oauth-authorization-server} route pattern in {@code KnoxIDFServiceDeploymentContributor}.
 */
@Path(".well-known/oauth-authorization-server")
@Produces(MediaType.APPLICATION_JSON)
public class OAuthServerMetadataRootResource extends OAuthServerMetadataResource {
}
