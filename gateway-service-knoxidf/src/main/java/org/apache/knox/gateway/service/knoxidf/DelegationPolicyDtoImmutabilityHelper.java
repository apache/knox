/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Defensive-copy helpers shared by {@link DelegationPolicyRequest} and
 * {@link DelegationPolicyResponse}, so a caller-held reference to a set/map passed into a setter
 * cannot mutate this DTO's state after the fact.
 */
final class DelegationPolicyDtoImmutabilityHelper {

  private DelegationPolicyDtoImmutabilityHelper() {
  }

  static Set<String> copyOf(Set<String> values) {
    if (values == null) {
      return Collections.emptySet();
    }
    // JSON arrays may contain JSON null elements, which Jackson deserializes as null.
    // Filter them out defensively: Set.copyOf() would throw NullPointerException on them,
    // and in some Jackson versions the NPE escapes readValue() uncaught as a RuntimeException
    // rather than being wrapped in JsonMappingException (IOException), landing in the
    // generic catch(RuntimeException) handler and returning a misleading 500 storage_error.
    final Set<String> result = new HashSet<>();
    for (String v : values) {
      if (v != null) {
        result.add(v);
      }
    }
    return Collections.unmodifiableSet(result);
  }

  static Map<String, Set<String>> copyResourcePolicy(Map<String, Set<String>> resourcePolicy) {
    final Map<String, Set<String>> source = (resourcePolicy != null) ? resourcePolicy : Collections.emptyMap();
    final Map<String, Set<String>> copy = new HashMap<>();
    for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
      if (entry.getKey() != null) {
        copy.put(entry.getKey(), copyOf(entry.getValue()));
      }
    }
    return Collections.unmodifiableMap(copy);
  }
}
