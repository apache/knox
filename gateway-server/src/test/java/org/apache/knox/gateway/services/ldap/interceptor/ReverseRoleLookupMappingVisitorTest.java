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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.api.ldap.model.filter.BranchNode;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.filter.OrNode;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.LDAPRolesLookupService;
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReverseRoleLookupMappingVisitorTest {
    private static final Function<String, String> GROUP_DN_CREATOR = name -> "cn=" + name + ",ou=groups,dc=proxy,dc=com";

    private SchemaManager schemaManager;

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    private ReverseRoleLookupMappingVisitor visitor(LDAPRolesLookupService service) {
        return new ReverseRoleLookupMappingVisitor(schemaManager, service, GROUP_DN_CREATOR);
    }

    private static LDAPRolesLookupService serviceReturning(String role, RoleAssignees assignees) throws Exception {
        LDAPRolesLookupService service = EasyMock.createStrictMock(LDAPRolesLookupService.class);
        EasyMock.expect(service.lookupRoleAssignees(role)).andReturn(assignees);
        EasyMock.replay(service);
        return service;
    }

    private static LDAPRolesLookupService unusedService() {
        LDAPRolesLookupService service = EasyMock.createStrictMock(LDAPRolesLookupService.class);
        EasyMock.replay(service);
        return service;
    }

    private static Set<String> childFilters(Object node) {
        return ((BranchNode) node).getChildren().stream().map(ExprNode::toString).collect(Collectors.toSet());
    }

    @Test
    public void testVisitWithNullReturnsNull() {
        assertNull(visitor(unusedService()).visit(null));
    }

    @Test
    public void testRoleIsReplacedWithUsersAndGroups() throws Exception {
        LDAPRolesLookupService service = serviceReturning("platform:admin",
                new RoleAssignees(List.of("alice", "bob"), List.of("engineering")));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=platform:admin,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertTrue(result instanceof OrNode);
        assertEquals(
                Set.of("(uid=alice)", "(uid=bob)", "(memberOf=cn=engineering,ou=groups,dc=proxy,dc=com)"),
                childFilters(result));
        EasyMock.verify(service);
    }

    @Test
    public void testRoleWithOnlyGroupsIsReplaced() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", new RoleAssignees(List.of(), List.of("g1")));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(Set.of("(memberOf=cn=g1,ou=groups,dc=proxy,dc=com)"), childFilters(result));
    }

    @Test
    public void testRoleWithoutAssigneesLeavesFilterUntouched() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", new RoleAssignees(List.of(), List.of()));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testNullAssigneeListsLeaveFilterUntouched() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", new RoleAssignees(null, null));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testNullUsersListWithGroupsIsMapped() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", new RoleAssignees(null, List.of("g1")));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(Set.of("(memberOf=cn=g1,ou=groups,dc=proxy,dc=com)"), childFilters(result));
    }

    @Test
    public void testNullGroupsListWithUsersIsMapped() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", new RoleAssignees(List.of("alice"), null));
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(Set.of("(uid=alice)"), childFilters(result));
    }

    @Test
    public void testNullAssigneesLeavesFilterUntouched() throws Exception {
        LDAPRolesLookupService service = serviceReturning("viewer", null);
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        Object result = filter.accept(visitor(service));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testOtherAttributesNeverReachTheService() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(uid=alice)");

        Object result = filter.accept(visitor(unusedService()));

        assertEquals("(uid=alice)", result.toString());
    }

    @Test
    public void testMemberOfPresenceFilterNeverReachesTheService() throws Exception {
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=*)");

        Object result = filter.accept(visitor(unusedService()));

        assertEquals(filter.toString(), result.toString());
    }

    @Test
    public void testMemberOfNestedInBranchesIsReplaced() throws Exception {
        LDAPRolesLookupService service = serviceReturning("admin", new RoleAssignees(List.of("alice"), List.of()));
        ExprNode filter = FilterParser.parse(schemaManager,
                "(&(objectClass=person)(|(uid=zed)(memberOf=cn=admin,ou=groups,dc=proxy,dc=com)))");

        Object result = filter.accept(visitor(service));

        String text = result.toString();
        assertTrue(text, text.contains("objectClass=person"));
        assertTrue(text, text.contains("(uid=zed)"));
        assertTrue(text, text.contains("(uid=alice)"));
        assertTrue(text, !text.contains("memberOf"));
    }

    @Test
    public void testMemberOfUnderNotIsReplaced() throws Exception {
        LDAPRolesLookupService service = serviceReturning("admin", new RoleAssignees(List.of("alice"), List.of()));
        ExprNode filter = FilterParser.parse(schemaManager, "(!(memberOf=cn=admin,ou=groups,dc=proxy,dc=com))");

        Object result = filter.accept(visitor(service));

        assertTrue(result.toString(), result.toString().startsWith("(!"));
        assertTrue(result.toString(), !result.toString().contains("memberOf"));
        assertTrue(result.toString(), result.toString().contains("(uid=alice)"));
    }

    @Test
    public void testServiceFailureIsReported() throws Exception {
        LDAPRolesLookupService service = EasyMock.createStrictMock(LDAPRolesLookupService.class);
        EasyMock.expect(service.lookupRoleAssignees("viewer")).andThrow(new IllegalStateException("backend down"));
        EasyMock.replay(service);
        ExprNode filter = FilterParser.parse(schemaManager, "(memberOf=cn=viewer,ou=groups,dc=proxy,dc=com)");

        RuntimeException e = assertThrows(RuntimeException.class, () -> filter.accept(visitor(service)));
        assertEquals("backend down", e.getCause().getMessage());
    }
}
