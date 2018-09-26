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
package org.apache.knox.gateway.deploy.impl;

import org.apache.knox.gateway.topology.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ShiroConfig {
  
  private Map<String, Map<String, String>> sections = new LinkedHashMap<String, Map<String, String>>();
 
  public ShiroConfig(Provider provider, String clusterName) {
    Map<String, String> params = provider.getParams();
    String name = null;
    String sectionName = null;
    String value = null;
    for(Entry<String, String> entry : params.entrySet()) {
      int sectionDot = entry.getKey().indexOf('.');
      if (sectionDot > 0) {
        sectionName = entry.getKey().substring(0, sectionDot);
        name = entry.getKey().substring(sectionDot + 1);
        value = entry.getValue().trim();
        if (value.startsWith("${ALIAS=") && value.endsWith("}")) {
          String baseName = name.substring(0, name.lastIndexOf("."));
          addNameValueToSection(baseName + ".clusterName", clusterName, sectionName);
          addNameValueToSection(name, "S" + value.substring(1), sectionName);
        } else {
          addNameValueToSection(name, value, sectionName);
        }
      }
    }
  }

  private void addNameValueToSection(String name, String value, String sectionName) {
    Map<String, String> section = sections.get(sectionName);
    if (section == null) {
      section = new LinkedHashMap<String, String>();
      sections.put(sectionName, section);
    }
    section.put(name, value);
  }
  
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for(Entry<String, Map<String, String>> section : sections.entrySet()) {
      sb.append("[").append(section.getKey()).append("]\n");
      for(Entry<String, String> entry : section.getValue().entrySet()) {
        sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
      }
    }
    return sb.toString();
  }
}
