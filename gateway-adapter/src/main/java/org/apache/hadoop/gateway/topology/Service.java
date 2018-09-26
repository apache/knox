/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
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
package org.apache.hadoop.gateway.topology;

import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Version;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Deprecated
public class Service extends org.apache.knox.gateway.topology.Service {
  @Override
  public String getRole() {
    return super.getRole();
  }

  @Override
  public void setRole(String role) {
    super.setRole(role);
  }

  @Override
  public String getName() {
    return super.getName();
  }

  @Override
  public void setName(String name) {
    super.setName(name);
  }

  @Override
  public Version getVersion() {
    return super.getVersion();
  }

  @Override
  public void setVersion(Version version) {
    super.setVersion(version);
  }

  @Override
  public List<String> getUrls() {
    return super.getUrls();
  }

  @Override
  public void setUrls(List<String> urls) {
    super.setUrls(urls);
  }

  @Override
  public String getUrl() {
    return super.getUrl();
  }

  @Override
  public void addUrl(String url) {
    super.addUrl(url);
  }

  @Override
  public Map<String, String> getParams() {
    return super.getParams();
  }

  @Override
  public Collection<Param> getParamsList() {
    return super.getParamsList();
  }

  @Override
  public void setParamsList(Collection<Param> params) {
    super.setParamsList(params);
  }

  @Override
  public void setParams(Map<String, String> params) {
    super.setParams(params);
  }

  @Override
  public void addParam(Param param) {
    super.addParam(param);
  }

  @Override
  public boolean equals(Object object) {
    return super.equals(object);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
