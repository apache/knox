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
package org.apache.knox.gateway.preauth.k8s;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpiffeIdTest {
    @Test
    public void testParseValidId() {
        Optional<SpiffeId> parsed = SpiffeId.parse("spiffe://cluster.local/ns/demo/sa/demo-app");
        assertTrue(parsed.isPresent());
        assertEquals("cluster.local", parsed.get().trustDomain());
        assertEquals("demo", parsed.get().namespace());
        assertEquals("demo-app", parsed.get().serviceAccount());
    }

    @Test
    public void testRejectNullAndEmpty() {
        assertFalse(SpiffeId.parse(null).isPresent());
        assertFalse(SpiffeId.parse("").isPresent());
        assertFalse(SpiffeId.parse("   ").isPresent());
    }

    @Test
    public void testRejectWrongScheme() {
        assertFalse(SpiffeId.parse("https://cluster.local/ns/demo/sa/foo").isPresent());
    }

    @Test
    public void testRejectMissingNsOrSaSegments() {
        assertFalse(SpiffeId.parse("spiffe://cluster.local/demo/sa/foo").isPresent());
        assertFalse(SpiffeId.parse("spiffe://cluster.local/ns/demo/foo").isPresent());
        assertFalse(SpiffeId.parse("spiffe://cluster.local/ns/demo").isPresent());
        assertFalse(SpiffeId.parse("spiffe://cluster.local").isPresent());
    }

    @Test
    public void testRejectExtraPathSegments() {
        assertFalse(SpiffeId.parse("spiffe://cluster.local/ns/demo/sa/foo/extra").isPresent());
    }

    @Test
    public void testRejectEmptyNamespaceOrServiceAccount() {
        assertFalse(SpiffeId.parse("spiffe://cluster.local/ns//sa/foo").isPresent());
        assertFalse(SpiffeId.parse("spiffe://cluster.local/ns/demo/sa/").isPresent());
    }

    @Test
    public void testRejectMalformedUri() {
        assertFalse(SpiffeId.parse("spiffe:// cluster.local/ns/demo/sa/foo").isPresent());
    }
}
