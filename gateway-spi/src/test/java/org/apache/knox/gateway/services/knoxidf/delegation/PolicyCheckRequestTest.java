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
package org.apache.knox.gateway.services.knoxidf.delegation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class PolicyCheckRequestTest {

    @Test
    public void testPolicyCheckRequestScopesFieldIsImmutable() {
        final Set<String> mutableScopes = new HashSet<>(Arrays.asList("read", "write"));
        final PolicyCheckRequest request = new PolicyCheckRequest(
                "oidc", "actor1", "subject1", Set.of("/api/v1"), mutableScopes, false);

        final Set<String> returnedScopes = request.getRequestedScopes();
        assertThrows("requestedScopes must be immutable", UnsupportedOperationException.class,
                () -> returnedScopes.add("admin"));
        assertThrows("requestedScopes must be immutable", UnsupportedOperationException.class,
                () -> returnedScopes.remove("read"));
    }

    @Test
    public void testPolicyCheckRequestResourcesFieldIsImmutable() {
        final Set<String> mutableResources = new HashSet<>(Set.of("/api/v1"));
        final PolicyCheckRequest request = new PolicyCheckRequest(
                "oidc", "actor1", "subject1", mutableResources, Set.of("read"), false);

        mutableResources.add("/api/v2");
        assertEquals("requestedResources must be defensively copied, not aliased",
                Set.of("/api/v1"), request.getRequestedResources());

        final Set<String> returnedResources = request.getRequestedResources();
        assertThrows("requestedResources must be immutable", UnsupportedOperationException.class,
                () -> returnedResources.add("/api/v2"));
        assertThrows("requestedResources must be immutable", UnsupportedOperationException.class,
                () -> returnedResources.remove("/api/v1"));
    }
}
