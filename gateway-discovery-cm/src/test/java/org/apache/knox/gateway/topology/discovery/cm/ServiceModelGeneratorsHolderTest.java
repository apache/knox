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

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

public class ServiceModelGeneratorsHolderTest {

    @Test
    public void testGetAllRoleTypesContainsGeneratorRoleTypes() {
        Set<String> roleTypes = ServiceModelGeneratorsHolder.getInstance().getAllRoleTypes();
        assertThat("Should contain role types declared by registered generators",
                roleTypes, hasItems("SOLR_SERVER", "NAMENODE", "ATLAS_SERVER", "HIVESERVER2"));
    }

    @Test
    public void testGetAllRoleTypesDoesNotContainUnknownRoleType() {
        Set<String> roleTypes = ServiceModelGeneratorsHolder.getInstance().getAllRoleTypes();
        assertThat("Should not contain a role type no generator declares",
                roleTypes.contains("NO_SUCH_ROLE_TYPE"), is(false));
        assertThat(roleTypes, not(hasItems("NO_SUCH_ROLE_TYPE")));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllRoleTypesIsUnmodifiable() {
        ServiceModelGeneratorsHolder.getInstance().getAllRoleTypes().add("SOME_ROLE_TYPE");
    }

}
