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
package org.apache.knox.gateway.topology.simple;

public enum ProviderOrder {
  WEBAPPSEC(1), AUTHENTICATION(2), FEDERATION(3), IDENTITY_ASSERTION(4), AUTHORIZATION(5), HOSTMAP(6), HA(7);

  final int ordinal;
  final String name;

  ProviderOrder(int ordinal) {
    this.ordinal = ordinal;
    this.name = name().replace("_", "-");
  }

  static int getOrdinalForRole(String role) {
    int count = 0;
    for (ProviderOrder providerOrder : values()) {
      count++;
      if (providerOrder.name.equalsIgnoreCase(role)) {
        return providerOrder.ordinal;
      }
    }
    return ++count;
  }
}
