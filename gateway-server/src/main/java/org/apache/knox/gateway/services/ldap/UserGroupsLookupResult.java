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
package org.apache.knox.gateway.services.ldap;

import java.util.List;

/**
 * The result of looking up a user's groups through the embedded LDAP server.
 *
 * @param groups the user's group (or, with the roles lookup interceptor active, role) names
 * @param rolesLookupApplied whether the roles lookup interceptor ran the roles lookup for the user. It
 *                           does so for each user entry the search returns, so this is false when the
 *                           user was not found, even if the interceptor is configured.
 */
public record UserGroupsLookupResult(List<String> groups, boolean rolesLookupApplied) {
}
