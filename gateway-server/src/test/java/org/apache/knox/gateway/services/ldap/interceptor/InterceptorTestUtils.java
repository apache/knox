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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;

public class InterceptorTestUtils {

    public static Entry assertNextEntryUid(EntryFilteringCursor cursor, String uid) throws Exception {
        assertTrue("Cursor should have another entry", cursor.next());
        Entry entry = cursor.get();
        Attribute uidAttr = entry.get("uid");
        assertEquals("Attribute should have only one value", 1, uidAttr.size());
        Value value = uidAttr.get();
        assertEquals("Uid should match " + uid, uid, value.getString());
        return entry;
    }
}
