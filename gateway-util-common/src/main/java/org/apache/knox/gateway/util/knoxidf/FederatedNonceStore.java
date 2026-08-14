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

/**
 * Holds the OIDC {@code nonce} that Knox generates and sends to a federated OP, keyed by the
 * federated login-session id (the {@code state} echoed by the OP). The value is written when the OP
 * authorization redirect is built and read once when the OP callback is processed, so the returned
 * id_token's {@code nonce} claim can be bound to this specific authorization request. Like the other
 * KnoxIDF artifact stores this is a JVM singleton (the redirect is built in one topology/webapp and
 * the callback handled in another, within the same JVM) and its entries are single-use: callers
 * {@code remove()} the nonce after verifying it so a replayed callback cannot reuse it.
 */
public class FederatedNonceStore extends KnoxIDFArtifactStore<String> {

    private static FederatedNonceStore instance;

    private FederatedNonceStore(long ttl) {
        super(ttl);
    }

    public static synchronized FederatedNonceStore getInstance(long ttl) {
        if (instance == null) {
            instance = new FederatedNonceStore(ttl);
        }
        return instance;
    }
}
