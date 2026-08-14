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
package org.apache.knox.gateway.services.knoxidf.federation;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FederatedIdentity {

    private final String id;
    private final String userId;
    private final String provider;
    private final String externalSubject;
    private final String externalIssuer;
    private final Instant createdAt;
    private final Map<String, String> attributes = new HashMap<>();

    public FederatedIdentity(String userId, String provider, String externalSubject, String externalIssuer,
                             Instant createdAt, Map<String, String> attributes) {
        this(UUID.randomUUID().toString(), userId, provider, externalSubject, externalIssuer, createdAt, attributes);
    }

    public FederatedIdentity(String id, String userId, String provider, String externalSubject, String externalIssuer,
                             Instant createdAt, Map<String, String> attributes) {
        this.id = id;
        this.userId = userId;
        this.provider = provider;
        this.externalSubject = externalSubject;
        this.externalIssuer = externalIssuer;
        this.createdAt = createdAt;
        if (attributes != null) {
            this.attributes.putAll(attributes);
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getExternalIssuer() {
        return externalIssuer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public void addAttribute(String key, String value) {
        attributes.put(key, value);
    }
}
