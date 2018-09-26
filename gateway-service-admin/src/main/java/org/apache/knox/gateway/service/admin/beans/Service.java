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
package org.apache.knox.gateway.service.admin.beans;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

@XmlAccessorType(XmlAccessType.NONE)
public class Service {
  @XmlElement
  private String role;
  @XmlElement
  private String name;
  @XmlElement
  private String version;
  @XmlElement(name="param")
  private List<Param> params;
  @XmlElement(name="url")
  private List<String> urls;

  public Service() {
  }

  public String getRole() {
    return role;
  }

  public void setRole( String role ) {
    this.role = role;
  }

  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public List<String> getUrls() {
    if (urls == null) {
      urls = new ArrayList<>();
    }
    return urls;
  }

  public void setUrls( List<String> urls ) {
    this.urls = urls;
  }

  public List<Param> getParams() {
    if (params == null) {
      params = new ArrayList<>();
    }
    return params;
  }

  public void setParams(List<Param> params) {
    this.params = params;
  }
}
