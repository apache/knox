/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.registry.impl;

import org.apache.knox.gateway.services.registry.ServiceDefEntry;

public class DefaultServiceDefEntry implements ServiceDefEntry {

  private String role;

  private String name;

  private String route;

  public DefaultServiceDefEntry(String role, String name, String route) {
    this.role = role;
    this.name = name;
    this.route = route;
  }

  @Override
  public String getRole() {
    return role;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getRoute() {
    return route;
  }

}

