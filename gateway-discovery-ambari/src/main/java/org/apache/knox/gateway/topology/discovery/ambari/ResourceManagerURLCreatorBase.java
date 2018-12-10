/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
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
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class ResourceManagerURLCreatorBase implements ServiceURLCreator {

  static final String CONFIG_SERVICE = "YARN";
  static final String CONFIG_TYPE    = "yarn-site";

  static final String WEBAPP_ADDRESS_HTTP  = "yarn.resourcemanager.webapp.address";
  static final String WEBAPP_ADDRESS_HTTPS = "yarn.resourcemanager.webapp.https.address";

  static final String SCHEME_HTTP  = "http";
  static final String SCHEME_HTTPS = "https";

  protected AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

  private AmbariCluster cluster;


  @Override
  public void init(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (getTargetService().equals(service)) {
      AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration(CONFIG_SERVICE, CONFIG_TYPE);
      if (sc != null) {
        Map<String, String> configProps = sc.getProperties();

        String httpPolicy = getHttpPolicy(configProps);

        // First, check if it's HA config
        boolean isHAEnabled = Boolean.parseBoolean(configProps.get("yarn.resourcemanager.ha.enabled"));
        if (isHAEnabled) {
          String rmSet = configProps.get("yarn.resourcemanager.ha.rm-ids");
          if (rmSet != null) {
            String[] rmIds = rmSet.split(",");
            for (String rmId : rmIds) {
              String address = configProps.get(getWebAppAddressPropertyName(httpPolicy) + "." + rmId.trim());
              if (address != null && !address.isEmpty()) {
                urls.add(createURL(address));
              }
            }
          }
        }

        // If no HA URLs were identified, then add the default webapp address URL
        if (urls.isEmpty()) {
          urls.add(createURL(configProps.get(getWebAppAddressPropertyName(httpPolicy))));
        }

      }
    }

    return urls;
  }


  protected abstract String createURL(String address);


  String getURLScheme() {
    return isHttps(getHttpPolicy()) ? SCHEME_HTTPS : SCHEME_HTTP;
  }


  private String getHttpPolicy() {
    String httpPolicy = null;
    AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration(CONFIG_SERVICE, CONFIG_TYPE);
    if (sc != null) {
      httpPolicy = getHttpPolicy(sc.getProperties());
    }
    return httpPolicy;
  }

  private String getHttpPolicy(Map<String, String> configProps) {
      return configProps.get("yarn.http.policy");
  }

  private boolean isHttps(String httpPolicy) {
    return "HTTPS_ONLY".equalsIgnoreCase(httpPolicy);
  }

  private String getWebAppAddressPropertyName(String httpPolicy) {
    return isHttps(httpPolicy) ? WEBAPP_ADDRESS_HTTPS : WEBAPP_ADDRESS_HTTP;
  }

}
