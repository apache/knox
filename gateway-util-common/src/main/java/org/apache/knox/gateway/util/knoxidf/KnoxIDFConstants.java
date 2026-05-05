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
package org.apache.knox.gateway.util.knoxidf;

import com.google.common.collect.Sets;

import java.util.Set;

public interface KnoxIDFConstants {
    String BASE_RESORCE_PATH = "knoxidf/api/v1";
    String AUTH_CODE = "authorization_code";
    String CLIENT_ID = "client_id";
    String REDIRECT_URI = "redirect_uri";
    String RESPONSE_TYPE = "response_type";
    Set<String> ALLOWED_RESPONSE_TYPES = Sets.newHashSet("code", "id_token", "code id_token");
    String SCOPE = "scope";
    String OFFLINE_ACCESS_SCOPE = "offline_access";
    Set<String> DEFAULT_SCOPES = Sets.newHashSet("openid", "profile", "email", OFFLINE_ACCESS_SCOPE);
    String OPENID_SCOPE = SCOPE + "=openid";
    String STATE = "state";
    String CODE = "code";
    String REFRESH_TOKEN = "refresh_token";
    String REFRESH_TOKEN_TTL= "refresh.token.ttl";
    long REFRESH_TOKEN_TTL_DEFAULT = 86400000L; // 1 day
    String CODE_RESPONSE_TYPE = RESPONSE_TYPE + "=" + CODE;
    String NONCE = "nonce";

    String TOKEN_ID_ATTRIBUTE = "X-Token-Id";
    String SCOPE_ATTRIBUTE = "X-Token-Scope";

    String FEDERATED_ID_TOKEN_PREFIX = "fed_id_";
    String FEDERATED_ACCESS_TOKEN_PREFIX = "fed_access_";
    String FEDERATED_OP_CONFIG_PREFIX = "federated.op.";
    String FEDERATED_OP_CONFIG_NAMES = FEDERATED_OP_CONFIG_PREFIX + "names";
}
