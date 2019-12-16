/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.collector;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceURLCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base ServiceURLCollector implementation
 */
public abstract class AbstractURLCollector implements ServiceURLCollector {

  @Override
  public List<String> collect(Map<String, List<ServiceModel>> roleModels) {
    List<String> urls;

    if (roleModels.isEmpty()) {
      urls = Collections.emptyList();
    } else if (roleModels.size() == 1) {
      urls = new ArrayList<>();
      List<ServiceModel> models =  roleModels.values().iterator().next();
      urls.addAll(getURLs(models));
    } else {
      urls = getURLs(roleModels);
    }
    return urls;
  }

  protected List<String> getURLs(Map<String, List<ServiceModel>> roleModels) {
    List<String> urls = new ArrayList<>();

    for (List<ServiceModel> models : roleModels.values()) {
      urls.addAll(getURLs(models));
    }

    return urls;
  }

  protected List<String> getURLs(List<ServiceModel> roleModels) {
    List<String> urls = new ArrayList<>();

    for (ServiceModel model : roleModels) {
      urls.add(model.getServiceUrl());
    }

    return urls;
  }

}
