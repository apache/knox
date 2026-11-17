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

import org.apache.commons.lang3.StringUtils;

import jakarta.servlet.ServletRequest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The JWTProvider's {@code jwt.allowed.grant.types}, passed to KnoxIDF as the
 * {@link KnoxIDFConstants#ALLOWED_GRANT_TYPES_ATTRIBUTE} request attribute. Unset or blank means
 * {@link #ALL}; any non-empty list also rejects a missing {@code grant_type}.
 */
public final class AllowedGrantTypes {
    public static final AllowedGrantTypes ALL = new AllowedGrantTypes(null);

    // Every grant the KnoxIDF token endpoint serves.
    private static final Set<String> SUPPORTED = Collections.unmodifiableSet(new LinkedHashSet<>(List.of(
            KnoxIDFConstants.AUTH_CODE, KnoxIDFConstants.REFRESH_TOKEN, KnoxIDFConstants.CLIENT_CREDENTIALS,
            KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE)));

    // null means unrestricted
    private final Set<String> grantTypes;

    private AllowedGrantTypes(Set<String> grantTypes) {
        this.grantTypes = grantTypes;
    }

    /** The list attached to the request, or {@link #ALL} when there is none. */
    public static AllowedGrantTypes from(ServletRequest request) {
        final Object attribute = request == null ? null : request.getAttribute(KnoxIDFConstants.ALLOWED_GRANT_TYPES_ATTRIBUTE);
        return attribute instanceof AllowedGrantTypes ? (AllowedGrantTypes) attribute : ALL;
    }

    public static AllowedGrantTypes parse(String configured) {
        final Set<String> grantTypes = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(configured)) {
            for (final String grantType : configured.split(",")) {
                if (StringUtils.isNotBlank(grantType)) {
                    grantTypes.add(grantType.trim());
                }
            }
        }
        return grantTypes.isEmpty() ? ALL : new AllowedGrantTypes(Collections.unmodifiableSet(grantTypes));
    }

    /** {@code none}, a supported grant type, or {@code other}, so caller input never reaches the log. */
    public static String auditName(String grantType) {
        if (StringUtils.isBlank(grantType)) {
            return "none";
        }
        return SUPPORTED.contains(grantType) ? grantType : "other";
    }

    public boolean isRestricted() {
        return grantTypes != null;
    }

    public boolean isAuthCodeAllowed() {
        return isAllowed(KnoxIDFConstants.AUTH_CODE);
    }

    public boolean isAllowed(String grantType) {
        return grantTypes == null || grantTypes.contains(grantType);
    }

    public Set<String> grantTypesSupported() {
        return grantTypes == null ? SUPPORTED : grantTypes;
    }
}
