/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.ldap;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.SchemaManager;

import java.util.Locale;
import java.util.Set;

public class LdapUtils {
    // Attributes whose values are distinguished names and therefore need remote->proxy
    // DN rewriting. Other attribute values (mail, description, ...) are copied verbatim.
    public static final String DISTINGUISHED_NAME_SYNTAX_OID = "1.3.6.1.4.1.1466.115.121.1.12";
    public static final Set<String> DN_VALUED_ATTRIBUTES = Set.of(
            "member", "uniquemember", "memberof", "manager", "owner", "seealso");

    public static boolean isGroupEntry(Entry entry) throws LdapException {
        final Attribute objectClass = entry.get("objectClass");
        if (objectClass == null) {
            return false;
        }
        for (Value value : objectClass) {
            final String objectClassName = value.getString();
            if ("groupOfNames".equalsIgnoreCase(objectClassName) || "groupOfUniqueNames".equalsIgnoreCase(objectClassName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUserEntry(Entry entry) throws LdapException {
        final Attribute objectClass = entry.get("objectClass");
        if (objectClass == null) {
            return false;
        }
        for (Value value : objectClass) {
            final String objectClassName = value.getString();
            if ("inetOrgPerson".equalsIgnoreCase(objectClassName)
                    || "person".equalsIgnoreCase(objectClassName)
                    || "organizationalPerson".equalsIgnoreCase(objectClassName)) {
                return true;
            }
        }
        return false;
    }

    public static String extractUsernameFromDn(Dn dn) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }

        try {
            return "uid".equalsIgnoreCase(dn.getRdn().getType())
                    ? dn.getRdn().getValue()
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String extractUsernameFromEntry(Entry entry, String... attributeNames) {
        String userName = null;
        for (String attributeName : attributeNames) {
            Attribute attribute = entry.get(attributeName);
            if (attribute != null) {
                try {
                    userName = attribute.getString();
                    if (userName != null) {
                        break;
                    }
                } catch (LdapException ignored) {
                }
            }
        }
        return userName;
    }

    public static String extractGroupName(Dn dn) {
        if (!dn.isEmpty()) {
            Rdn rdn = dn.getRdn();
            if (rdn.getType().equalsIgnoreCase("cn")) {
                return rdn.getValue();
            }
        }
        return null;
    }


    /**
     * Returns whether the attribute is DN-valued.
     *
     * @param attributeName the name of the attribute
     * @param schemaManager the schema manager
     * @return true if the attribute is DN-valued
     */
    public static boolean isDnValued(String attributeName, SchemaManager schemaManager) {
        return DN_VALUED_ATTRIBUTES.contains(attributeName.toLowerCase(Locale.ROOT)) ||
                isDnValued(schemaManager.getAttributeType(attributeName));
    }

    /**
     * Returns whether the attribute type is DN-valued.
     *
     * @param attributeType the attribute type
     * @return true if the attribute is DN-valued
     */
    public static boolean isDnValued(AttributeType attributeType) {
        return attributeType != null && DISTINGUISHED_NAME_SYNTAX_OID.equalsIgnoreCase(attributeType.getSyntaxOid());
    }
}
