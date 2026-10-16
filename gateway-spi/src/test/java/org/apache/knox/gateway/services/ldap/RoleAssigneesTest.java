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
package org.apache.knox.gateway.services.ldap;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoleAssigneesTest {

    @Test
    public void testConstructorAndGetters() {
        RoleAssignees assignees = new RoleAssignees(List.of("alice"), List.of("engineering", "admins"));
        assertEquals(List.of("alice"), assignees.getUsers());
        assertEquals(List.of("engineering", "admins"), assignees.getGroups());
    }

    @Test
    public void testDefaultConstructorAndSetters() {
        RoleAssignees assignees = new RoleAssignees();
        assertNull(assignees.getUsers());
        assertNull(assignees.getGroups());
        assignees.setUsers(List.of("bob"));
        assignees.setGroups(List.of());
        assertEquals(List.of("bob"), assignees.getUsers());
        assertTrue(assignees.getGroups().isEmpty());
    }

    @Test
    public void testToStringContainsMembers() {
        String text = new RoleAssignees(List.of("alice"), List.of("engineering")).toString();
        assertTrue(text.contains("alice"));
        assertTrue(text.contains("engineering"));
    }
}
