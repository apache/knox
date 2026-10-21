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

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.filter.BranchNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MemberOfMappingVisitorTest {
    private static final String GROUP_DN = "cn=engineering,ou=groups,dc=hadoop,dc=apache,dc=org";

    private SchemaManager schemaManager;
    private final List<String> lookedUpDns = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    private MemberOfMappingVisitor visitor(Entry groupEntry) {
        return new MemberOfMappingVisitor(schemaManager, (dn, sm) -> {
            lookedUpDns.add(dn);
            return groupEntry;
        }, dn -> dn.startsWith("uid="));
    }

    private Entry group(String attribute, String... members) throws Exception {
        Entry entry = new DefaultEntry(schemaManager, GROUP_DN);
        entry.add("objectClass", "groupOfNames");
        entry.add("cn", "engineering");
        for (String member : members) {
            entry.add(attribute, member);
        }
        return entry;
    }

    private static Set<String> childFilters(Object node) {
        return ((BranchNode) node).getChildren().stream().map(ExprNode::toString).collect(Collectors.toSet());
    }

    @Test
    public void testVisitWithNullReturnsNull() throws Exception {
        assertNull(visitor(group("member")).visit(null));
    }

    @Test
    public void testMemberOfIsReplacedWithUsersAndNestedGroups() throws Exception {
        Entry entry = group("member",
                "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org",
                "cn=nested,ou=groups,dc=hadoop,dc=apache,dc=org");
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        Object result = filter.accept(visitor(entry));

        assertEquals(Set.of("(uid=alice)", "(memberOf=cn=nested,ou=groups,dc=hadoop,dc=apache,dc=org)"), childFilters(result));
        assertEquals(List.of(GROUP_DN), lookedUpDns);
    }

    @Test
    public void testUniqueMembersAreMappedToo() throws Exception {
        Entry entry = group("uniqueMember", "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        Object result = filter.accept(visitor(entry));

        assertEquals(Set.of("(uid=bob)"), childFilters(result));
    }

    @Test
    public void testMembersAndUniqueMembersAreCombined() throws Exception {
        Entry entry = group("member", "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        entry.add("uniqueMember", "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        Object result = filter.accept(visitor(entry));

        assertEquals(new HashSet<>(List.of("(uid=alice)", "(uid=bob)")), childFilters(result));
    }

    @Test
    public void testMemberOfIsLeftUntouchedWhenGroupNotFound() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        Object result = filter.accept(visitor(null));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testMemberOfIsLeftUntouchedWhenGroupHasNoMembers() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        Object result = filter.accept(visitor(group("member")));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testOtherAttributesAreLeftUntouched() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(uid=alice)");

        Object result = filter.accept(visitor(group("member", "uid=x,dc=y")));

        assertEquals("(uid=alice)", result.toString());
        assertTrue(lookedUpDns.isEmpty());
    }

    @Test
    public void testMemberOfPresenceFilterIsLeftUntouched() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=*)");

        Object result = filter.accept(visitor(group("member", "uid=x,dc=y")));

        assertEquals(filter.toString(), result.toString());
        assertTrue(lookedUpDns.isEmpty());
    }

    @Test
    public void testMemberOfNestedInBranchesIsReplaced() throws Exception {
        Entry entry = group("member", "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org");
        ExprNode filter = FilterParser.parse(schemaManager,
                "(&(objectClass=person)(|(uid=zed)(memberOf=" + GROUP_DN + ")))");

        Object result = filter.accept(visitor(entry));

        String text = result.toString();
        assertTrue(text, text.contains("(uid=zed)"));
        assertTrue(text, text.contains("(uid=alice)"));
        assertTrue(text, text.contains("objectClass=person"));
        assertTrue(text, !text.contains("memberOf"));
    }

    @Test
    public void testLookupFailureIsReported() throws Exception {
        MemberOfMappingVisitor failing = new MemberOfMappingVisitor(schemaManager, (dn, sm) -> {
            throw new IllegalStateException("backend down");
        }, dn -> true);
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=" + GROUP_DN + ")");

        RuntimeException e = assertThrows(RuntimeException.class, () -> filter.accept(failing));
        assertEquals("backend down", e.getCause().getMessage());
    }
}
