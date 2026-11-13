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
import org.apache.directory.api.ldap.model.schema.ObjectClass;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.UsageEnum;
import org.apache.directory.api.ldap.schema.loader.JarLdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory class for creating SchemaManager instances.
 */
public class SchemaManagerFactory {

    public static SchemaManager createSchemaManager() throws IOException, LdapException {
        // Create and load schema manager manually
        JarLdifSchemaLoader loader = new JarLdifSchemaLoader();
        SchemaManager schemaManager = new DefaultSchemaManager(loader);
        schemaManager.loadAllEnabled();

        List<AttributeType> userAttributeTypes = new ArrayList<>();
        List<AttributeType> groupAttributeTypes = new ArrayList<>();
        getCustomAttributeTypes(userAttributeTypes, groupAttributeTypes);
        getActiveDirectoryAttributeTypes(userAttributeTypes, groupAttributeTypes);

        Set<AttributeType> allAttributeTypes = new HashSet<>();
        allAttributeTypes.addAll(userAttributeTypes);
        allAttributeTypes.addAll(groupAttributeTypes);
        for (AttributeType attributeType : allAttributeTypes) {
            schemaManager.add(attributeType);
        }

        ObjectClass personObjectClass = schemaManager.lookupObjectClassRegistry("person");
        personObjectClass.unlock();
        for (AttributeType attributeType : userAttributeTypes) {
            personObjectClass.addMayAttributeTypes(attributeType);
        }
        personObjectClass.lock();

        ObjectClass groupOfNamesObjectClass = schemaManager.lookupObjectClassRegistry("groupofnames");
        groupOfNamesObjectClass.unlock();
        for (AttributeType attributeType : groupAttributeTypes) {
            groupOfNamesObjectClass.addMayAttributeTypes(attributeType);
        }
        groupOfNamesObjectClass.lock();

        return schemaManager;
    }

    private static void getCustomAttributeTypes(List<AttributeType> userAttributes, List<AttributeType> groupAttributes) {
        AttributeType memberOfAttrType = new AttributeType("1.2.840.113556.1.2.102");
        memberOfAttrType.setNames("memberOf");
        memberOfAttrType.setSchemaName("other");
        memberOfAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.12");
        memberOfAttrType.setDescription("attribute specifies the distinguished names of the groups to which this object belongs");
        memberOfAttrType.setSingleValued(false);
        userAttributes.add(memberOfAttrType);
        groupAttributes.add(memberOfAttrType);

        AttributeType nsAccountLockAttrType = new AttributeType("2.16.840.1.113730.3.1.610");
        nsAccountLockAttrType.setNames("nsAccountLock");
        nsAccountLockAttrType.setSchemaName("other");
        nsAccountLockAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.15");
        nsAccountLockAttrType.setEqualityOid("caseIgnoreMatch");
        nsAccountLockAttrType.setDescription("Operational attribute to administratively lock/inactivate accounts");
        nsAccountLockAttrType.setSingleValued(true);
        userAttributes.add(nsAccountLockAttrType);
    }

    private static void getActiveDirectoryAttributeTypes(List<AttributeType> userAttributes, List<AttributeType> groupAttributes) {
        AttributeType sAMAccountNameAttrType = new AttributeType("1.2.840.113556.1.4.221");
        sAMAccountNameAttrType.setNames("sAMAccountName");
        sAMAccountNameAttrType.setSchemaName("other");
        sAMAccountNameAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.15");
        sAMAccountNameAttrType.setDescription("Microsoft Active Directory sAMAccountName attribute for compatibility");
        sAMAccountNameAttrType.setEqualityOid("2.5.13.2"); // caseignorematch
        sAMAccountNameAttrType.setSubstringOid("2.5.13.4"); // caseignoresubstringsmatch
        sAMAccountNameAttrType.setSingleValued(true);
        userAttributes.add(sAMAccountNameAttrType);

        AttributeType userAccountControlAttrType = new AttributeType("1.2.840.113556.1.4.8");
        userAccountControlAttrType.setNames("userAccountControl");
        userAccountControlAttrType.setSchemaName("other");
        userAccountControlAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.27");
        userAccountControlAttrType.setDescription("Microsoft Active Directory  User Account Control attribute for compatibility");
        userAccountControlAttrType.setEqualityOid("2.5.13.14"); // integermatch
        userAccountControlAttrType.setOrderingOid("2.5.13.15"); // integerorderingmatch
        userAccountControlAttrType.setSingleValued(true);
        userAttributes.add(userAccountControlAttrType);

        // whenChanged and objectGUID exist on every Active Directory object (users and groups) and
        // are read-only. whenChanged is operational (returned only when explicitly requested), which
        // is the main reason KNOX-3574 passes requested attributes through to the backend.
        AttributeType whenChangedAttrType = new AttributeType("1.2.840.113556.1.2.3");
        whenChangedAttrType.setNames("whenChanged");
        whenChangedAttrType.setSchemaName("other");
        whenChangedAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.24"); // generalized time
        whenChangedAttrType.setEqualityOid("2.5.13.27"); // generalizedtimematch
        whenChangedAttrType.setOrderingOid("2.5.13.28"); // generalizedtimeorderingmatch
        whenChangedAttrType.setDescription("Microsoft Active Directory whenChanged modification timestamp (operational)");
        whenChangedAttrType.setSingleValued(true);
        whenChangedAttrType.setUserModifiable(false);
        whenChangedAttrType.setUsage(UsageEnum.DIRECTORY_OPERATION);
        userAttributes.add(whenChangedAttrType);
        groupAttributes.add(whenChangedAttrType);

        AttributeType objectGuidAttrType = new AttributeType("1.2.840.113556.1.4.2");
        objectGuidAttrType.setNames("objectGUID");
        objectGuidAttrType.setSchemaName("other");
        objectGuidAttrType.setSyntaxOid("1.3.6.1.4.1.1466.115.121.1.40"); // octet string
        objectGuidAttrType.setEqualityOid("2.5.13.17"); // octetstringmatch
        objectGuidAttrType.setDescription("Microsoft Active Directory objectGUID unique identifier");
        objectGuidAttrType.setSingleValued(true);
        objectGuidAttrType.setUserModifiable(false);
        // objectGUID is server-generated and immutable. A non-user-modifiable attribute must be
        // operational in a strict schema (RFC 4512), otherwise registration fails.
        objectGuidAttrType.setUsage(UsageEnum.DIRECTORY_OPERATION);
        userAttributes.add(objectGuidAttrType);
        groupAttributes.add(objectGuidAttrType);
    }
}
