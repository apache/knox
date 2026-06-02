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
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;

public class LdapUtils {

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
}
