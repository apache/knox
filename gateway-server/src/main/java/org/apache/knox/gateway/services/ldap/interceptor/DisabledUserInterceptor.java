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
package org.apache.knox.gateway.services.ldap.interceptor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DisabledUserInterceptor extends BaseInterceptor {

    private static final String UAC_ATTRIBUTENAME = "useraccountcontrol";
    // Active Directory UserAccountControl Bitmasks
    private static final int UAC_ACCOUNTDISABLE = 0x0002;

    private static final String NSACCOUNTLOCK_ATTRIBUTE = "nsaccountlock";
    private static final String NSACCOUNTLOCK_DISABLED_VALUE = "true";

    private boolean removeDisabledUsers;

    public DisabledUserInterceptor(String name, Map<String, String> config) {
        this(name, Boolean.parseBoolean(config.getOrDefault("removeDisabledUsers", "false")));
    }

    protected DisabledUserInterceptor(String name, boolean removeDisabledUsers) {
        super(name);
        this.removeDisabledUsers = removeDisabledUsers;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        // First execute the interceptor chain to get the results
        List<Entry> filteredEntries = List.of();
        try (EntryFilteringCursor originalResults = next(ctx)) {
            List<Entry> originalEntries = new ArrayList<>();
            try {
                while (originalResults.next()) {
                    originalEntries.add(originalResults.get());
                }
            } catch (CursorException e) {
                // rethrow exception on incomplete iteration
                throw new LdapException(e);
            }
            filteredEntries = filterDisabledUsers(originalEntries);
        } catch (IOException e) {
            // IOException would only occur after finishing iterating over results
            // we can ignore this exception and return the filtered entries
        }
        return new EntryFilteringCursorImpl(new ListCursor<>(filteredEntries), ctx, schemaManager);
    }

    @VisibleForTesting
    List<Entry> filterDisabledUsers(List<Entry> originalEntries) throws LdapException {
        List<Entry> filteredEntries = new ArrayList<>();
        for (Entry entry : originalEntries) {
            if (!isAccountDisabled(entry)) {
                filteredEntries.add(entry);
            } else if (!removeDisabledUsers) {
                // translate disabled to use nsaccountlock flag if it isn't already set
                if (!entry.contains(NSACCOUNTLOCK_ATTRIBUTE, NSACCOUNTLOCK_DISABLED_VALUE)) {
                    if (entry.containsAttribute(NSACCOUNTLOCK_ATTRIBUTE)) {
                        entry.removeAttributes(NSACCOUNTLOCK_ATTRIBUTE);
                    }
                    entry.add(NSACCOUNTLOCK_ATTRIBUTE, NSACCOUNTLOCK_DISABLED_VALUE);
                }
                filteredEntries.add(entry);
            } // don't add entry to filteredEntries if removeDisabledUsers flag is set
        }
        return filteredEntries;
    }

    private boolean isAccountDisabled(Entry entry) {
        return isNsAccountLockDisabled(entry) || isUacAccountDisabled(entry);
    }

    private boolean isNsAccountLockDisabled(Entry entry) {
        return entry.contains(NSACCOUNTLOCK_ATTRIBUTE, NSACCOUNTLOCK_DISABLED_VALUE);
    }

    private boolean isUacAccountDisabled(Entry entry) {
        // Check userAccountControl attribute for Active Directory
        Attribute uacAttribute = entry.get(UAC_ATTRIBUTENAME);
        if (uacAttribute != null) {
            String uacString = uacAttribute.get().getString();
            try {
                int uacMask = Integer.parseInt(uacString);
                return (uacMask & UAC_ACCOUNTDISABLE) == UAC_ACCOUNTDISABLE;
            } catch (NumberFormatException e) {
                // Assume account is active if value is unparseable
                return false;
            }
        }
        // account is not disabled using the UAC attribute
        return false;
    }
}
