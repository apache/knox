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

import java.util.Objects;

/**
 * The non-secret part of a {@link FederatedOpConfiguration} needed for the OP authorization
 * redirect, safe to store in the authorization flow state.
 */
public final class FederatedOpLoginOption {
    private final String name;
    private final String clientId;
    private final String authorizeEndpoint;
    private final String authorizeCallback;

    public FederatedOpLoginOption(String name, String clientId, String authorizeEndpoint, String authorizeCallback) {
        this.name = name;
        this.clientId = clientId;
        this.authorizeEndpoint = authorizeEndpoint;
        this.authorizeCallback = authorizeCallback;
    }

    public static FederatedOpLoginOption of(FederatedOpConfiguration federatedOpConfiguration) {
        return new FederatedOpLoginOption(federatedOpConfiguration.getName(), federatedOpConfiguration.getClientId(),
                federatedOpConfiguration.getAuthorizeEndpoint(), federatedOpConfiguration.getAuthorizeCallback());
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    public String getAuthorizeEndpoint() {
        return authorizeEndpoint;
    }

    public String getAuthorizeCallback() {
        return authorizeCallback;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FederatedOpLoginOption)) {
            return false;
        }
        final FederatedOpLoginOption that = (FederatedOpLoginOption) o;
        return Objects.equals(name, that.name) && Objects.equals(clientId, that.clientId)
                && Objects.equals(authorizeEndpoint, that.authorizeEndpoint)
                && Objects.equals(authorizeCallback, that.authorizeCallback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, clientId, authorizeEndpoint, authorizeCallback);
    }
}
