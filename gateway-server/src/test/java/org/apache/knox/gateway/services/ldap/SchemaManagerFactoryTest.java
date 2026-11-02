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
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.junit.Test;

public class SchemaManagerFactoryTest {
    @Test
    public void testMemberOfUsesDistinguishedNameMatch() throws Exception {
        SchemaManager schemaManager = SchemaManagerFactory.createSchemaManager();

        assertEquals("distinguishedNameMatch", schemaManager.getAttributeType("memberOf").getEquality().getName());
    }

    @Test
    public void testMemberOfValuesMatchIgnoringDnCaseAndSpacing() throws Exception {
        SchemaManager schemaManager = SchemaManagerFactory.createSchemaManager();
        Entry entry = new DefaultEntry(schemaManager, "uid=jsmith,ou=people,dc=example,dc=com");
        entry.add("memberOf", "CN=Engineering,OU=Groups,DC=Example,DC=com");

        assertTrue(entry.contains("memberOf", "cn=engineering, ou=groups, dc=example, dc=com"));
    }
}
