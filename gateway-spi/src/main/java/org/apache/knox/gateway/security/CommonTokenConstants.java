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
package org.apache.knox.gateway.security;

public interface CommonTokenConstants {

    String GRANT_TYPE = "grant_type";

    String CLIENT_CREDENTIALS = "client_credentials";

    String CLIENT_ID = "client_id";

    String CLIENT_SECRET = "client_secret";

    String AUTH_CODE = "authorization_code";

    /**
     * RFC 8707 (Resource Indicators) / RFC 8693 (Token Exchange) {@code resource} request parameter:
     * the target service for which the token is requested, expressed as an absolute URI.
     */
    String RESOURCE = "resource";

    /**
     * RFC 8693 (Token Exchange) {@code audience} request parameter: the logical name of the target
     * service for which the token is requested.
     */
    String AUDIENCE = "audience";

    /**
     * Request attribute used to convey the requested audiences parsed from an RFC 8693 token-exchange
     * body ({@code resource}/{@code audience}) from the JWTProvider's token-exchange handler to the
     * downstream KNOXTOKEN service, which mints the token. When present it takes precedence over the
     * {@code resource} query parameter. The value is a {@code List<String>}.
     */
    String REQUESTED_AUDIENCES_REQUEST_ATTR = "knox.token.exchange.requested.audiences";

}
