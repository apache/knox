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
package org.apache.knox.gateway.topology;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Provider {

  private String role;
  private String name;
  private boolean enabled;
  private Map<String, String> params = new LinkedHashMap<>();

  public Provider() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public void setParams(Map<String, String> params) {
    this.params = params;
  }

  public void addParam(Param param) {
    params.put(param.getName(), param.getValue());
  }

  public Collection<Param> getParamsList(){

    ArrayList<Param> paramList = new ArrayList<>();

    for(Map.Entry<String, String> entry : params.entrySet()){
      Param p = new Param();
      p.setName(entry.getKey());
      p.setValue(entry.getValue());
      paramList.add(p);
    }

    return paramList;
  }

  public String getRole() {
    return role;
  }

  public void setRole( String role ) {
    this.role = role;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name)
                                .append(role)
                                .append(params)
                                .append(enabled)
                                .build();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Provider)) {
      return false;
    }

    Provider other = (Provider) obj;
    return (new EqualsBuilder()).append(name, other.name)
                                .append(role, other.role)
                                .append(params, other.params)
                                .append(enabled, other.enabled)
                                .build();
  }

}
