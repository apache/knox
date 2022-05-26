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
package org.apache.knox.gateway.service.auth.bearer;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.util.Locale;

import static javax.ws.rs.core.Response.ok;

/**
 * The service populates HTTP "Authorization" header with the Bearer Token, obtained from an environment variable.
 * This implementation assumes that the token is not rotated as it never gets exposed to the end-user.
 * Consider alternative ways of obtaining tokens(e.g. from files) and implement caching/refreshing mechanism if token
 * rotation is required.
 */
@Path(AuthBearerResource.RESOURCE_PATH)
public class AuthBearerResource {
    static final String RESOURCE_PATH = "/api/v1/bearer/authorize";
    static final String DEFAULT_BEARER_AUTH_TOKEN_ENV = "BEARER_AUTH_TOKEN";
    static final String BEARER_AUTH_TOKEN_ENV = "auth.bearer.token.env";
    static final String HEADER_FORMAT = "Bearer %s";

    private String token;
    private TokenGetter tokenGetter;

    @Context
    HttpServletResponse response;

    @Context
    ServletContext context;

    public AuthBearerResource() {
        tokenGetter = new EnvironmentTokenGetter();
    }

    @PostConstruct
    public void init() throws ServletException {
        String tokenName = context.getInitParameter(BEARER_AUTH_TOKEN_ENV);
        if (tokenName == null) {
            tokenName = DEFAULT_BEARER_AUTH_TOKEN_ENV;
        }

        token = tokenGetter.getToken(tokenName);
        if (token == null) {
            throw new ServletException(String.format(Locale.ROOT,"Token environment variable '%s' is not set", tokenName));
        }
    }

    @GET
    public Response doGet() {
        response.addHeader(HttpHeaders.AUTHORIZATION, String.format(Locale.ROOT,HEADER_FORMAT, token));
        return ok().build();
    }

    /**
     * Defines the TokenGetter to use. Useful for testing.
     * @param tokenGetter The {@link TokenGetter} to use.
     */
    void setTokenGetter(TokenGetter tokenGetter) {
        this.tokenGetter = tokenGetter;
    }
}
