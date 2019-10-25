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
package org.apache.knox.gateway.service.definition;

import java.util.Comparator;

/**
 * A {@link Comparator} implementation for {@link ServiceDefinition} using the
 * following fields in the following order for comparison:
 * <ol>
 * <li>name</li>
 * <li>role</li>
 * <li>version</li>
 * </ol>
 */
public class ServiceDefinitionComparator implements Comparator<ServiceDefinition> {

  @Override
  public int compare(ServiceDefinition serviceDefinition, ServiceDefinition otherServiceDefinition) {
    if (serviceDefinition == null || otherServiceDefinition == null) {
      throw new IllegalArgumentException("One (or both) of the supplied service definitions is null");
    }
    final int byName = serviceDefinition.getName().compareTo(otherServiceDefinition.getName());
    final int byRole = serviceDefinition.getRole().compareTo(otherServiceDefinition.getRole());
    final int byVersion = serviceDefinition.getVersion().compareTo(otherServiceDefinition.getVersion());
    return byName != 0 ? byName : byRole != 0 ? byRole : byVersion;
  }

}
