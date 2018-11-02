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
package org.apache.knox.gateway.security.principal;

public interface PrincipalMapper {

  /**
   * Load the internal principal mapping table from the provided
   * string value which conforms to the following semicolon delimited format:
   * actual[,another-actual]=mapped;...
   *
   * @param principalMapping semicolon delimited format of principal mapping
   * @param groupMapping semicolon delimited format of principal mapping
   * @throws PrincipalMappingException Exception if principal mapping cannot be loaded
   */
  void loadMappingTable(String principalMapping, String groupMapping)
      throws PrincipalMappingException;

  /**
   * Acquire a mapped principal name from the mapping table
   * as appropriate. Otherwise, the provided principalName
   * will be used.
   *
   * @param principalName principal name to look up in the mapping table
   * @return principal name to be used in the assertion
   */
  String mapUserPrincipal(String principalName);

  /**
   * Acquire array of group principal names from the mapping table
   * as appropriate. Otherwise, return null.
   *
   * @param principalName principal name to look up in the mapping table
   * @return group principal names to be used in the assertion
   */
  String[] mapGroupPrincipal(String principalName);
}