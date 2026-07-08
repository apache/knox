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

import org.apache.directory.api.ldap.model.cursor.ListCursor;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursorImpl;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;

import java.util.List;

/**
 * Interceptor for testing. This interceptor will return a Cursor of a List
 * of configured Entries.
 */
public class ConfigurableEntriesTestInterceptor extends BaseInterceptor {
    private List<Entry> entries;
    private EntryFilteringCursor cursor;

    public ConfigurableEntriesTestInterceptor(String name) {
        super(name);
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }

    public EntryFilteringCursor getCursor() {
        return cursor;
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext searchContext) throws LdapException {
        cursor = new EntryFilteringCursorImpl(new ListCursor<>(entries), searchContext, schemaManager);
        return cursor;
    }
}
