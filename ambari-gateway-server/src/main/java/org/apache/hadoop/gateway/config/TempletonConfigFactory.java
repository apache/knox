/**
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
package org.apache.hadoop.gateway.config;

import org.apache.hadoop.gateway.topology.ClusterComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TempletonConfigFactory implements ResourceConfigFactory {

  private static Set<String> ROLES = createSupportedRoles();

  private static Set<String> createSupportedRoles() {
    Set<String> roles = new HashSet<String>();
    roles.add( "TEMPLETON" );
    return Collections.unmodifiableSet( roles );
  }

  @Override
  public Set<String> getSupportedRoles() {
    return ROLES;
  }

  @Override
  public Collection<Config> createResourceConfig( Config clusterConfig, ClusterComponent clusterComponent ) {
    //TODO: Implement this.
    return null;
  }
}

