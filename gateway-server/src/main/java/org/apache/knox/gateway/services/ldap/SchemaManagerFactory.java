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

import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.LdapSyntax;
import org.apache.directory.api.ldap.model.schema.MatchingRule;
import org.apache.directory.api.ldap.model.schema.ObjectClass;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.schema.loader.JarLdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;

import java.io.IOException;

/**
 * Factory class for creating SchemaManager instances.
 */
public class SchemaManagerFactory {

    public static SchemaManager createSchemaManager() throws IOException, LdapException {
        // Create and load schema manager manually
        JarLdifSchemaLoader loader = new JarLdifSchemaLoader();
        SchemaManager schemaManager = new DefaultSchemaManager(loader);
        schemaManager.loadAllEnabled();

        // Add Custom schemas
        AttributeType memberOfAttrType = new AttributeType("1.2.840.113556.1.2.102");
        memberOfAttrType.setNames(new String[]{"memberOf"});
        memberOfAttrType.setSchemaName("other");
        memberOfAttrType.setSyntax(new LdapSyntax("1.3.6.1.4.1.1466.115.121.1.12"));
        memberOfAttrType.setDescription("attribute specifies the distinguished names of the groups to which this object belongs");
        memberOfAttrType.setSingleValued(false);
        schemaManager.add(memberOfAttrType);

        AttributeType sAMAccountNameAttrType = new AttributeType("1.2.840.113556.1.4.221");
        sAMAccountNameAttrType.setNames(new String[]{"sAMAccountName"});
        sAMAccountNameAttrType.setSchemaName("other");
        sAMAccountNameAttrType.setSyntax(new LdapSyntax("1.3.6.1.4.1.1466.115.121.1.15"));
        sAMAccountNameAttrType.setDescription("Microsoft sAMAccountName attribute for compatibility");
        sAMAccountNameAttrType.setEquality(new MatchingRule("caseignorematch"));
        sAMAccountNameAttrType.setSubstring(new MatchingRule("caseignoresubstringsmatch"));
        schemaManager.add(sAMAccountNameAttrType);

        ObjectClass personObjectClass = schemaManager.lookupObjectClassRegistry("person");
        personObjectClass.unlock();
        personObjectClass.addMayAttributeTypes(memberOfAttrType, sAMAccountNameAttrType);
        personObjectClass.lock();

        return schemaManager;
    }
}
