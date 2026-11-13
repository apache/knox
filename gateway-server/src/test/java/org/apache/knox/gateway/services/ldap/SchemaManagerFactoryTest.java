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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.ObjectClass;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.UsageEnum;
import org.junit.BeforeClass;
import org.junit.Test;

public class SchemaManagerFactoryTest {

    private static SchemaManager schemaManager;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    /**
     * Each hardcoded attribute type must register successfully. add() silently fails (without
     * throwing) when a referenced syntax or matching rule is missing from the bundled schema, so
     * this guards against a typo in a syntax/matching-rule OID leaving the attribute unregistered.
     */
    @Test
    public void testHardcodedAttributeTypesAreRegistered() throws Exception {
        assertAttributeType("memberOf", "1.2.840.113556.1.2.102");
        assertAttributeType("nsAccountLock", "2.16.840.1.113730.3.1.610");
        assertAttributeType("sAMAccountName", "1.2.840.113556.1.4.221");
        assertAttributeType("userAccountControl", "1.2.840.113556.1.4.8");
        assertAttributeType("whenChanged", "1.2.840.113556.1.2.3");
        assertAttributeType("objectGUID", "1.2.840.113556.1.4.2");
    }

    @Test
    public void testWhenChangedIsOperationalSingleValued() throws Exception {
        AttributeType whenChanged = schemaManager.getAttributeType("whenChanged");
        assertNotNull(whenChanged);
        assertEquals("1.2.840.113556.1.2.3", whenChanged.getOid());
        assertEquals(UsageEnum.DIRECTORY_OPERATION, whenChanged.getUsage());
        assertTrue(whenChanged.isSingleValued());
        assertFalse(whenChanged.isUserModifiable());
        assertNotNull("whenChanged syntax must resolve against the bundled schema", whenChanged.getSyntax());
    }

    @Test
    public void testObjectGuidIsReadOnlySingleValued() throws Exception {
        AttributeType objectGuid = schemaManager.getAttributeType("objectGUID");
        assertNotNull(objectGuid);
        assertEquals("1.2.840.113556.1.4.2", objectGuid.getOid());
        assertEquals(UsageEnum.DIRECTORY_OPERATION, objectGuid.getUsage());
        assertTrue(objectGuid.isSingleValued());
        assertFalse(objectGuid.isUserModifiable());
        assertNotNull("objectGUID syntax must resolve against the bundled schema", objectGuid.getSyntax());
    }

    /**
     * whenChanged and objectGUID appear on all AD objects, so they must be allowed on both user
     * (person) and group (groupOfNames) entries.
     */
    @Test
    public void testUniversalAdAttributesAllowedOnUsersAndGroups() throws Exception {
        ObjectClass person = schemaManager.lookupObjectClassRegistry("person");
        ObjectClass groupOfNames = schemaManager.lookupObjectClassRegistry("groupofnames");
        for (String attribute : new String[] {"whenChanged", "objectGUID"}) {
            String oid = schemaManager.getAttributeType(attribute).getOid();
            assertTrue(attribute + " should be allowed on person", containsOid(person, oid));
            assertTrue(attribute + " should be allowed on groupOfNames", containsOid(groupOfNames, oid));
        }
    }

    // The registry may hold a different AttributeType instance than the one added to the object
    // class, so membership is checked by OID rather than object identity.
    private static boolean containsOid(ObjectClass objectClass, String oid) {
        return objectClass.getMayAttributeTypes().stream().anyMatch(at -> oid.equals(at.getOid()));
    }

    private static void assertAttributeType(String name, String expectedOid) throws Exception {
        assertTrue(name + " should be registered by OID",
                schemaManager.getAttributeTypeRegistry().contains(expectedOid));
        AttributeType attributeType = schemaManager.getAttributeType(name);
        assertNotNull(name + " should be resolvable by name", attributeType);
        assertEquals(expectedOid, attributeType.getOid());
    }
}
