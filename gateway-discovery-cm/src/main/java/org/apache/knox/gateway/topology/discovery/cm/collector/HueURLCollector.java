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
import org.apache.knox.gateway.topology.discovery.cm.model.hue.HueLBServiceModelGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ServiceURLCollector for the Hue service
 */
public class HueURLCollector extends AbstractURLCollector {

  private static final String SCHEME_HTTP  = "http";
  private static final String SCHEME_HTTPS = "https";

  @Override
  protected List<String> getURLs(Map<String, List<ServiceModel>> roleModels) {
    List<String> urls = new ArrayList<>();

    if (roleModels.containsKey(HueLBServiceModelGenerator.ROLE_TYPE)) {
      List<String> lbUrls = getURLs(roleModels.get(HueLBServiceModelGenerator.ROLE_TYPE));
      // KNOX-1921
      // The Hue load balancer use the HUE_SERVER role ssl_enabled configuration as its own SSL enablement flag.
      // Rather than jumping through hoops to get the HUE_SERVER configuration, use the HUE_SERVER role URL scheme to
      // determine whether SSL is enabled or not.
      if (roleModels.get("HUE_SERVER").get(0).getServiceUrl().startsWith(SCHEME_HTTPS)) {
        // If the HUE_SERVER URLs have the http scheme, modify the load balancer URLs accordingly
        for (String url : lbUrls) {
          urls.add(url.replace(SCHEME_HTTP, SCHEME_HTTPS));
        }
      } else {
        urls.addAll(lbUrls);
      }
    } else {
      for (List<ServiceModel> models : roleModels.values()) {
        urls.addAll(getURLs(models));
      }
    }

    return urls;
  }

}
