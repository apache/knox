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
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DuplicateUserFilteringInterceptor extends BaseInterceptor {

    public DuplicateUserFilteringInterceptor(String name) {
        super(name);
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
                originalResults.close();
            } catch (CursorException e) {
                // rethrow exception on incomplete iteration
                throw new LdapException(e);
            }
            filteredEntries = filterDuplicateUsers(originalEntries);
        } catch (IOException e) {
            // IOException would only occur after finishing iterating over results
            // we can ignore this exception and return the filtered entries
        }
        return new EntryFilteringCursorImpl(new ListCursor<>(filteredEntries), ctx, schemaManager);
    }

    @VisibleForTesting
    List<Entry> filterDuplicateUsers(List<Entry> originalEntries) {
        Set<Value> seenUids = new HashSet<>();
        List<Entry> filteredEntries = new ArrayList<>();

        for (Entry entry : originalEntries) {
            Attribute uid = entry.get("uid");
            if (uid == null) {
                // keep entry because it's not a user
                filteredEntries.add(entry);
            } else {
                Value uidValue =  uid.get();
                if (!seenUids.contains(uidValue)) {
                    filteredEntries.add(entry);
                    seenUids.add(uidValue);
                }
            }
        }
        return filteredEntries;
    }
}
