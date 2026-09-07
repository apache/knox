/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class TypeNameFilterTest {

    @Test
    public void testExcludedNamesIsNull() {
        TypeNameFilter filter = new TypeNameFilter(null);
        assertThat("Should exclude nothing", filter.isExcluded("HIVESERVER2"), is(false));
    }

    @Test
    public void testExcludedNamesIsEmpty() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet());
        assertThat("Should exclude nothing", filter.isExcluded("HIVESERVER2"), is(false));
    }

    @Test
    public void testExcludeWhenExactMatch() {
        TypeNameFilter filter = new TypeNameFilter(Collections.singleton("HIVESERVER2"));
        assertThat("Should exclude exact match", filter.isExcluded("HIVESERVER2"), is(true));
    }

    @Test
    public void testExcludeWhenIgnoreCaseMatch() {
        TypeNameFilter filter = new TypeNameFilter(Collections.singleton("hiveServer2"));
        assertThat("Should exclude exact match", filter.isExcluded("HIVESERVER2"), is(true));
    }


    @Test
    public void testExcludeMultipleTypesWithExactMatch() {
        Set<String> excludedTypeNames =
                new HashSet<>(Arrays.asList("GATEWAY", "HIVESERVER2"));
        TypeNameFilter filter = new TypeNameFilter(excludedTypeNames);
        assertThat("Should exclude exact match", filter.isExcluded("HIVESERVER2"), is(true));
    }

    @Test
    public void testExcludeMultipleTypesWithIgnoreCaseMatch() {
        Set<String> excludedTypeNames =
                new HashSet<>(Arrays.asList("Gateway", "HiveserVER2"));
        TypeNameFilter filter = new TypeNameFilter(excludedTypeNames);
        assertThat("Should exclude exact match", filter.isExcluded("HIVESERVER2"), is(true));
    }

    @Test
    public void testRequiredTypeIsNotExcluded() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet(), Collections.singleton("HIVESERVER2"));
        assertThat("Required type should not be excluded", filter.isExcluded("HIVESERVER2"), is(false));
    }

    @Test
    public void testNonRequiredTypeIsExcludedWhenAllowListNonEmpty() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet(), Collections.singleton("HIVESERVER2"));
        assertThat("Type absent from a non-empty allow-list should be excluded", filter.isExcluded("DATANODE"), is(true));
    }

    @Test
    public void testEmptyRequiredTypesAppliesNoAllowListRestriction() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet(), Collections.emptySet());
        assertThat("Empty allow-list should not exclude anything", filter.isExcluded("DATANODE"), is(false));
    }

    @Test
    public void testNullRequiredTypesAppliesNoAllowListRestriction() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet(), null);
        assertThat("Null allow-list should not exclude anything", filter.isExcluded("DATANODE"), is(false));
    }

    @Test
    public void testRequiredTypeMatchIsCaseInsensitive() {
        TypeNameFilter filter = new TypeNameFilter(Collections.emptySet(), Collections.singleton("HiveServer2"));
        assertThat("Required type match should be case-insensitive", filter.isExcluded("HIVESERVER2"), is(false));
    }

    @Test
    public void testExcludedTypeWinsOverAllowList() {
        TypeNameFilter filter =
                new TypeNameFilter(Collections.singleton("HIVESERVER2"), Collections.singleton("HIVESERVER2"));
        assertThat("Deny-list should take precedence over allow-list", filter.isExcluded("HIVESERVER2"), is(true));
    }

}
