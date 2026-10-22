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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RoleAssignmentTest {

    @Test
    public void testDisplayValueWithScope() {
        assertEquals("platform:admin", new RoleAssignment("platform", "admin").getDisplayValue());
    }

    @Test
    public void testDisplayValueWithoutScope() {
        assertEquals("admin", new RoleAssignment(null, "admin").getDisplayValue());
    }

    @Test
    public void testDisplayValueWithEmptyScope() {
        assertEquals("admin", new RoleAssignment("", "admin").getDisplayValue());
    }

    @Test
    public void testDisplayValueWithoutName() {
        assertNull(new RoleAssignment("platform", null).getDisplayValue());
    }

    @Test
    public void testCreateFromDisplayValueWithScope() {
        RoleAssignment assignment = RoleAssignment.createFromDisplayValue("platform:admin");
        assertEquals("platform", assignment.getScope());
        assertEquals("admin", assignment.getName());
    }

    @Test
    public void testCreateFromDisplayValueWithoutScope() {
        RoleAssignment assignment = RoleAssignment.createFromDisplayValue("admin");
        assertNull(assignment.getScope());
        assertEquals("admin", assignment.getName());
    }

    @Test
    public void testCreateFromDisplayValueWithMultipleSeparators() {
        RoleAssignment assignment = RoleAssignment.createFromDisplayValue("a:b:c");
        assertEquals("a", assignment.getScope());
        assertEquals("b:c", assignment.getName());
    }

    @Test
    public void testDisplayValueRoundTrip() {
        for (String value : new String[] {"platform:admin", "admin"}) {
            assertEquals(value, RoleAssignment.createFromDisplayValue(value).getDisplayValue());
        }
    }
}
