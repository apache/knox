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

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public enum OIDCScope {
    OPENID(new HashSet<>(Collections.singletonList("sub"))),
    PROFILE(new HashSet<>(Arrays.asList(
            "name", "family_name", "given_name",
            "middle_name", "nickname", "preferred_username",
            "profile", "picture", "website", "gender",
            "birthdate", "zoneinfo", "locale", "updated_at"
    ))),
    EMAIL(new HashSet<>(Arrays.asList("email", "email_verified"))),
    ADDRESS(new HashSet<>(Collections.singletonList("address"))),
    PHONE(new HashSet<>(Arrays.asList("phone_number", "phone_number_verified"))),
    ROLES(new HashSet<>(Collections.singletonList("roles"))); // custom extension

    private final Set<String> claims;

    OIDCScope(Set<String> claims) {
        this.claims = Collections.unmodifiableSet(new HashSet<>(claims));
    }

    public Set<String> getClaims() {
        return claims;
    }

    public static Set<String> claimsForScopes(String scopeString) {
        Set<String> result = new HashSet<>();
        if (StringUtils.isEmpty(scopeString)) {
            return result;
        }

        for (String s : scopeString.split("\\s+")) {
            try {
                OIDCScope scope = OIDCScope.valueOf(s.toUpperCase(Locale.US));
                result.addAll(scope.getClaims());
            } catch (IllegalArgumentException ignored) {
                // ignore unknown scopes
            }
        }
        return result;
    }
}
