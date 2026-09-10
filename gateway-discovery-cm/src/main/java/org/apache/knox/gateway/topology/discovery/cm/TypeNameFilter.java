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
package org.apache.knox.gateway.topology.discovery.cm;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeNameFilter {
    private final Set<String> excludedTypeNames;
    private final Set<String> requiredTypeNames;

    /**
     * @param excludedTypeNames a deny-list of type names that are always excluded
     * @param requiredTypeNames an allow-list of type names; any type not contained in it
     *                          is excluded. An empty (or null) allow-list excludes every type.
     */
    public TypeNameFilter(Collection<String> excludedTypeNames, Collection<String> requiredTypeNames) {
        this.excludedTypeNames = mapToLowerCase(excludedTypeNames);
        this.requiredTypeNames = mapToLowerCase(requiredTypeNames);
    }

    public boolean isExcluded(String roleType) {
        final String lower = roleType.toLowerCase(Locale.ROOT);
        if (excludedTypeNames.contains(lower)) {
            return true;
        }
        return !requiredTypeNames.contains(lower);
    }

    private Set<String> mapToLowerCase(Collection<String> items) {
        if (items == null) {
            return Collections.emptySet();
        }
        return items.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

}
