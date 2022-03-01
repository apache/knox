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
package org.apache.knox.gateway.identityasserter.common.filter;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.knox.gateway.plang.AbstractSyntaxTree;
import org.apache.knox.gateway.plang.Parser;
import org.junit.Test;

@SuppressWarnings("PMD.NonStaticInitializer")
public class VirtualGroupMapperTest {
    private Parser parser = new Parser();
    private VirtualGroupMapper mapper;

    @Test
    public void testWithEmptyConfig() {
        mapper = new VirtualGroupMapper(Collections.emptyMap());
        assertEquals(Collections.emptySet(), virtualGroups("user1", emptyList()));
    }

    @Test
    public void testEverybodyGroup() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
                put("everybody", parser.parse("true"));
        }});
        assertEquals(setOf("everybody"), virtualGroups("user1", emptyList()));
        assertEquals(setOf("everybody"), virtualGroups("user2", asList("a", "b", "c")));
    }

    @Test
    public void testNobodyGroup() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("nobody", parser.parse("false"));
        }});
        assertEquals(0, virtualGroups("user1", emptyList()).size());
        assertEquals(0, virtualGroups("user2", asList("a", "b", "c")).size());
    }

    @Test
    public void testMember() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("vg1", parser.parse("(member 'g1')"));
            put("vg2", parser.parse("(member 'g2')"));
            put("both", parser.parse("(and (member 'g1') (member 'g2'))"));
            put("none", parser.parse("(not (or (member 'g1') (member 'g2')))"));
        }});
        assertEquals(setOf("vg1"), virtualGroups("user1", singletonList("g1")));
        assertEquals(setOf("vg2"), virtualGroups("user2", singletonList("g2")));
        assertEquals(setOf("vg1","vg2", "both"), virtualGroups("user3", asList("g1", "g2")));
        assertEquals(setOf("none"), virtualGroups("user4", singletonList("g4")));
    }

    @Test
    public void testAtLeastOne() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("at-least-one", parser.parse("(!= 0 (size groups))"));
        }});
        assertEquals(0, virtualGroups("user1", emptyList()).size());
        assertEquals(setOf("at-least-one"), virtualGroups("user2", singletonList("g1")));
        assertEquals(setOf("at-least-one"), virtualGroups("user3", asList("g1", "g2")));
        assertEquals(setOf("at-least-one"), virtualGroups("user4", asList("g1", "g2", "g3")));
    }

    @Test
    public void testEmptyGroup() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("empty", parser.parse("(= 0 (size groups))"));
        }});
        assertEquals(setOf("empty"), virtualGroups("user1", emptyList()));
        assertEquals(0, virtualGroups("user2", singletonList("any")).size());
    }

    @Test
    public void testMatchUser() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("users", parser.parse("(match username 'user_\\d+')"));
        }});
        assertEquals(setOf("users"), virtualGroups("user_1", emptyList()));
        assertEquals(setOf("users"), virtualGroups("user_2", emptyList()));
        assertEquals(0, virtualGroups("user2", emptyList()).size());
    }

    @Test
    public void testMatchGroup() {
        mapper = new VirtualGroupMapper(new HashMap<String, AbstractSyntaxTree>(){{
            put("grp", parser.parse("(match groups 'grp_\\d+')"));
        }});
        assertEquals(setOf("grp"), virtualGroups("user1", singletonList("grp_1")));
        assertEquals(setOf("grp"), virtualGroups("user2", asList("any", "grp_2")));
        assertEquals(0, virtualGroups("user3", singletonList("grp2")).size());
        assertEquals(0, virtualGroups("user4", emptyList()).size());
    }

    private Set<String> virtualGroups(String user1, List<String> ldapGroups) {
        return mapper.mapGroups(user1, new HashSet<>(ldapGroups), null);
    }

    private static Set<String> setOf(String... strings) {
        return new HashSet<>(Arrays.asList(strings));
    }
}