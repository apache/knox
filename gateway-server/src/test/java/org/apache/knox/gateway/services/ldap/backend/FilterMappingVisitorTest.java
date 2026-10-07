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

import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.services.ldap.SchemaManagerFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FilterMappingVisitorTest {
    private static final Function<String, String> IDENTITY_DN_CONVERTER = dn -> dn;

    private SchemaManager schemaManager;

    @Before
    public void setUp() throws Exception {
        schemaManager = SchemaManagerFactory.createSchemaManager();
    }

    @Test
    public void testVisitWithNullReturnsNull() {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("uid", "inetOrgPerson", "groupOfNames", schemaManager, IDENTITY_DN_CONVERTER);
        assertNull(visitor.visit(null));
    }

    @Test
    public void testVisitMapsUidAttributeToConfiguredIdentifier() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("sAMAccountName", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(uid=alice)");

        final Object result = node.accept(visitor);

        assertEquals("(sAMAccountName=alice)", result.toString());
    }

    @Test
    public void testVisitDoesNotRemapUidWhenIdentifierIsAlreadyUid() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("uid", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(uid=alice)");

        final Object result = node.accept(visitor);

        assertEquals("(uid=alice)", result.toString());
    }

    @Test
    public void testVisitRewritesDnValuedAttributeValueUsingDnConverter() throws Exception {
        final Function<String, String> dnConverter =
                dn -> dn.replace("dc=proxy,dc=com", "dc=hadoop,dc=apache,dc=org");
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("uid", "person", "posixGroup", schemaManager, dnConverter);
        final ExprNode node =
                FilterParser.parse(schemaManager, "(member=uid=alice,ou=people,dc=proxy,dc=com)");

        final Object result = node.accept(visitor);

        assertEquals("(member=uid=alice,ou=people,dc=hadoop,dc=apache,dc=org)", result.toString());
    }

    @Test
    public void testVisitMapsGroupOfNamesObjectClassLiteral() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("uid", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(objectClass=groupOfNames)");

        final Object result = node.accept(visitor);

        assertEquals("(objectClass=posixGroup)", result.toString());
    }

    @Test
    public void testVisitMapsInetOrgPersonObjectClassLiteral() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("uid", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(objectClass=inetOrgPerson)");

        final Object result = node.accept(visitor);

        assertEquals("(objectClass=person)", result.toString());
    }

    @Test
    public void testVisitPassesThroughUnrelatedAttribute() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("sAMAccountName", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(cn=engineering)");

        final Object result = node.accept(visitor);

        assertEquals("(cn=engineering)", result.toString());
    }

    @Test
    public void testVisitRecursesIntoBranchNodeChildren() throws Exception {
        final FilterMappingVisitor visitor =
                new FilterMappingVisitor("sAMAccountName", "person", "posixGroup", schemaManager, IDENTITY_DN_CONVERTER);
        final ExprNode node = FilterParser.parse(schemaManager, "(&(uid=alice)(objectClass=inetOrgPerson))");

        final Object result = node.accept(visitor);

        final String resultString = result.toString();
        assertTrue(resultString.contains("sAMAccountName=alice"));
        assertTrue(resultString.contains("objectClass=person"));
    }
}
