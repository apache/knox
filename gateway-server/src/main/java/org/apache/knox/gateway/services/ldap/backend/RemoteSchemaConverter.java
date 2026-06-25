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

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

public class RemoteSchemaConverter {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    // Proxy configuration
    private final String proxyBaseDn;  // Base DN for proxy entries (e.g., dc=proxy,dc=com)
    private final String proxyUserSearchBase;
    private final String proxyGroupSearchBase;

    // Backend configuration
    private final String remoteBaseDn;  // Base DN for remote server searches (e.g., dc=hadoop,dc=apache,dc=org)
    private final String remoteUserSearchBase;
    private final String remoteGroupSearchBase;
    private final String remoteUserIdentifierAttribute;
    private final String remoteUserObjectClass;
    private final String remoteGroupObjectClass;

    public RemoteSchemaConverter(String proxyBaseDn,
                                 String proxyUserSearchBase,
                                 String proxyGroupSearchBase,
                                 String remoteBaseDn,
                                 String remoteUserSearchBase,
                                 String remoteGroupSearchBase,
                                 String remoteUserIdentifierAttribute,
                                 String remoteUserObjectClass,
                                 String remoteGroupObjectClass) {
        this.proxyBaseDn = proxyBaseDn;
        this.proxyUserSearchBase = proxyUserSearchBase;
        this.proxyGroupSearchBase = proxyGroupSearchBase;
        this.remoteBaseDn = remoteBaseDn;
        this.remoteUserSearchBase = remoteUserSearchBase;
        this.remoteGroupSearchBase = remoteGroupSearchBase;
        this.remoteUserIdentifierAttribute = remoteUserIdentifierAttribute;
        this.remoteUserObjectClass = remoteUserObjectClass;
        this.remoteGroupObjectClass = remoteGroupObjectClass;
    }

    /**
     * Creates a proxy entry from a backend source entry with all required attributes.
     * This method standardizes the conversion of backend LDAP entries to proxy entries,
     * preserving the backend DN and copying all standard user attributes.
     *
     * @param sourceEntry The entry from the backend LDAP server
     * @param schemaManager The schemaManager
     * @return A new Entry with backend DN and all copied attributes
     * @throws LdapException if entry creation or attribute copying fails
     */
    public Entry convertRemoteEntryToProxyEntry(Entry sourceEntry, SchemaManager schemaManager) throws LdapException {
        // Standard proxy approach: return entry with backend DN unchanged
        // This preserves DN integrity for bind operations and DN references
        Entry entry = new DefaultEntry(schemaManager);
        entry.setDn(sourceEntry.getDn());

        // Copy all known AttributeTypes as-is from backend response
        for (Attribute attribute : sourceEntry.getAttributes()) {
            copyAttribute(sourceEntry, entry, attribute.getId());
        }

        // Map identifier attribute to uid for consistency if needed
        if (!"uid".equals(remoteUserIdentifierAttribute)) {
            Attribute idAttr = sourceEntry.get(remoteUserIdentifierAttribute);
            if (idAttr != null) {
                entry.add("uid", idAttr.getString());
            }
        }

        // replace userObjectClass and groupObjectClass object classes
        Attribute objectClassAttribute = sourceEntry.get("objectclass");
        if (objectClassAttribute.contains(remoteGroupObjectClass)) {
            entry.remove("objectclass", remoteGroupObjectClass);
            entry.add("objectclass", "groupofnames");
        }
        if (objectClassAttribute.contains(remoteUserObjectClass)) {
            entry.remove("objectclass", remoteUserObjectClass);
            entry.add("objectclass", "inetOrgPerson");
        }

        return entry;
    }

    /**
     * Converts an LDAP search filter from proxy attributes to remote attributes.
     * @param filter the filter
     * @param schemaManager the schema manager
     * @return the converted filter
     * @throws Exception if the filter cannot be parsed
     */
    public String convertProxyFilterToRemoteFilter(String filter, SchemaManager schemaManager) throws Exception {
        FilterMappingVisitor filterMappingVisitor = new FilterMappingVisitor(remoteUserIdentifierAttribute, remoteUserObjectClass, remoteGroupObjectClass, schemaManager);

        // Filter likely has already been annotated by other interceptors.
        // Clean the filter by removing any modifications or annotations
        String rawFilter = filter.replaceAll(":\\[.*?\\]", "").replaceAll("=\\s+", "=");
        ExprNode cleanNode = FilterParser.parse(schemaManager, rawFilter);

        return cleanNode.accept(filterMappingVisitor).toString();
    }

    /**
     * Copy attributes from a remote entry to a proxy entry. DN values are converted from
     * the remote base to the proxy base
     * @param source The remote entry
     * @param target The proxy entry
     * @param attributeName The name of the attribute to copy
     */
    public void copyAttribute(Entry source, Entry target, String attributeName) {
        final Attribute attribute = source.get(attributeName);
        if (attribute != null) {
            // Copy all values of the attribute (important for multi-valued attributes like objectClass)
            for (Value value : attribute) {
                String valueString = convertRemoteDnToProxyDn(value.toString());
                if (!target.contains(attributeName, valueString)) {
                    try {
                        target.add(attributeName, valueString);
                    } catch (LdapException e) {
                        LOG.ldapAttributeCopyError(e);
                    }
                }
            }
        }
    }

    /**
     * Converts a remote dn string to a proxy dn string. This also works for search base strings.
     * @param string the remote dn or search base
     * @return the proxy search base
     */
    public String convertRemoteDnToProxyDn(String string) {
        return string == null ?
                null :
                string.replaceAll("(?i)" + remoteGroupSearchBase, proxyGroupSearchBase)
                .replaceAll("(?i)" + remoteUserSearchBase, proxyUserSearchBase)
                .replaceAll("(?i)" + remoteBaseDn, proxyBaseDn);
    }

    /**
     * Converts a proxy dn string to a remote dn string. This also works for search base strings.
     * @param string the proxy dn or search base
     * @return the remote search base
     */
    public String convertProxyDnToRemoteDn(String string) {
        return string == null ?
                null :
                string.replaceAll("(?i)" + proxyGroupSearchBase, remoteGroupSearchBase)
                .replaceAll("(?i)" + proxyUserSearchBase, remoteUserSearchBase)
                .replaceAll("(?i)" + proxyBaseDn, remoteBaseDn);
    }
}
