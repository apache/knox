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
package org.apache.knox.gateway.dto;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HomePageProfile {
  static final String GPI_PREFIX = "gpi_";
  static final String GPI_VERSION = GPI_PREFIX + "version";
  static final String GPI_CERT = GPI_PREFIX + "cert";
  static final String GPI_ADMIN_UI = GPI_PREFIX + "admin_ui";
  static final String GPI_ADMIN_API = GPI_PREFIX + "admin_api";
  static final String GPI_METADATA_API = GPI_PREFIX + "md_api";
  static final String GPI_TOKENS = GPI_PREFIX + "tokens";
  static final String TOPOLOGIES = "topologies";
  static final String TOPOLOGY_PREFIX = "top_";
  static final String ALL_TOPOLOGIES = TOPOLOGY_PREFIX + "all";

  private final Map<String, String> profileElements = new HashMap<>();

  public HomePageProfile(Collection<String> profileConfiguration) {
    addElement(GPI_VERSION, profileConfiguration);
    addElement(GPI_CERT, profileConfiguration);
    addElement(GPI_ADMIN_UI, profileConfiguration);
    addElement(GPI_ADMIN_API, profileConfiguration);
    addElement(GPI_METADATA_API, profileConfiguration);
    addElement(GPI_TOKENS, profileConfiguration);
    addTopologies(profileConfiguration);
  }

  private void addTopologies(Collection<String> profileConfiguration) {
    final Set<String> topologies = new HashSet<>();
    profileConfiguration.forEach(config -> {
      if (config.startsWith(TOPOLOGY_PREFIX)) {
        topologies.add(config.substring(TOPOLOGY_PREFIX.length()));
      }
    });
    profileElements.put(TOPOLOGIES, String.join(",", topologies));
  }

  private void addElement(String element, Collection<String> profileConfiguration) {
    if (profileConfiguration.contains(element)) {
      profileElements.put(element, Boolean.TRUE.toString());
    } else {
      profileElements.put(element, Boolean.FALSE.toString());
    }
  }

  public Map<String, String> getProfileElements() {
    return profileElements;
  }

  public static Collection<String> getFullProfileElements() {
    return Arrays.asList(GPI_VERSION, GPI_CERT, GPI_ADMIN_UI, GPI_ADMIN_API, GPI_METADATA_API, GPI_TOKENS);
  }

  public static Collection<String> getThinProfileElemens() {
    return Arrays.asList(GPI_VERSION, GPI_CERT);
  }

  public static Collection<String> getTokenProfileElements() {
    return Arrays.asList(GPI_VERSION, GPI_TOKENS);
  }
}
