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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Policy {

  private String name;

  private String role;

  @XmlElement(name = "param")
  private List<CustomDispatch.XMLParam> params = new ArrayList<>();

  @XmlAttribute
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @XmlAttribute
  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public void addParam( DispatchParam param ) {
    params.add(new CustomDispatch.XMLParam(param.getName(), param.getValue()));
  }

  public Map<String, String> getParams() {
    Map<String, String> result = new LinkedHashMap<>();
    if( params != null ) {
      for (CustomDispatch.XMLParam p : params) {
        result.put(p.name, p.value);
      }
    }
    return result;
  }
}
