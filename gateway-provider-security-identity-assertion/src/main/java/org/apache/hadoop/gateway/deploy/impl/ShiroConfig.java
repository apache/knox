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
package org.apache.hadoop.gateway.deploy.impl;

import org.apache.hadoop.gateway.topology.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ShiroConfig {
  private Map<String, Map<String, String>> sections = new HashMap<String, Map<String, String>>();
  
  public ShiroConfig(Provider provider) {
    Map<String, String> params = provider.getParams();
    String name = null;
    String sectionName = null;
    for(Entry<String, String> entry : params.entrySet()) {
      int sectionDot = entry.getKey().indexOf('.');
      if (sectionDot > 0) {
        sectionName = entry.getKey().substring(0, sectionDot);
        name = entry.getKey().substring(sectionDot + 1);
        addNameValueToSection(name, entry.getValue(), sectionName);
      }
    }
  }

  private void addNameValueToSection(String name, String value, String sectionName) {
    Map<String, String> section = sections.get(sectionName);
    if (section == null) {
      section = new HashMap<String, String>();
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
