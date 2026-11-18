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
package org.apache.knox.gateway.services.ldap.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AttributeNameMapperTest {

    @Test
    public void testNullAndBlankConfigYieldEmptyMapper() {
        assertTrue(AttributeNameMapper.fromConfig(null).isEmpty());
        assertTrue(AttributeNameMapper.fromConfig("").isEmpty());
        assertTrue(AttributeNameMapper.fromConfig("   ").isEmpty());
    }

    @Test
    public void testUnmappedNamesPassThrough() {
        final AttributeNameMapper mapper = AttributeNameMapper.fromConfig(null);
        assertEquals("cn", mapper.toProxyName("cn"));
        assertEquals("cn", mapper.toRemoteName("cn"));
        assertEquals("*", mapper.toRemoteName("*"));
        assertNull(mapper.toProxyName(null));
        assertNull(mapper.toRemoteName(null));
    }

    @Test
    public void testBidirectionalMapping() {
        final AttributeNameMapper mapper = AttributeNameMapper.fromConfig("whenChanged:modifyTimestamp");
        assertFalse(mapper.isEmpty());
        // remote -> proxy (entry conversion)
        assertEquals("modifyTimestamp", mapper.toProxyName("whenChanged"));
        // proxy -> remote (requested attributes and filters)
        assertEquals("whenChanged", mapper.toRemoteName("modifyTimestamp"));
        // names not involved in the mapping are returned unchanged in both directions
        assertEquals("cn", mapper.toProxyName("cn"));
        assertEquals("modifyTimestamp", mapper.toProxyName("modifyTimestamp"));
    }

    @Test
    public void testLookupsAreCaseInsensitive() {
        final AttributeNameMapper mapper = AttributeNameMapper.fromConfig("whenChanged:modifyTimestamp");
        assertEquals("modifyTimestamp", mapper.toProxyName("WHENCHANGED"));
        assertEquals("whenChanged", mapper.toRemoteName("MODIFYtimestamp"));
    }

    @Test
    public void testMultiplePairsAndWhitespaceAreParsed() {
        final AttributeNameMapper mapper =
                AttributeNameMapper.fromConfig(" whenChanged : modifyTimestamp , whenCreated:createTimestamp ");
        assertEquals("modifyTimestamp", mapper.toProxyName("whenChanged"));
        assertEquals("createTimestamp", mapper.toProxyName("whenCreated"));
        assertEquals("whenCreated", mapper.toRemoteName("createTimestamp"));
    }

    @Test
    public void testMalformedPairsAreSkipped() {
        // No colon, empty remote side, empty proxy side, and a stray empty segment are all ignored,
        // but a valid pair in the same list still registers.
        final AttributeNameMapper mapper =
                AttributeNameMapper.fromConfig("noColon, :onlyProxy, onlyRemote:, ,whenChanged:modifyTimestamp");
        assertEquals("modifyTimestamp", mapper.toProxyName("whenChanged"));
        assertEquals("noColon", mapper.toProxyName("noColon"));
        assertEquals("onlyRemote", mapper.toProxyName("onlyRemote"));
    }

    @Test
    public void testConfigWithOnlyMalformedPairsIsEmpty() {
        assertTrue(AttributeNameMapper.fromConfig("noColon, :x, y:").isEmpty());
    }
}
