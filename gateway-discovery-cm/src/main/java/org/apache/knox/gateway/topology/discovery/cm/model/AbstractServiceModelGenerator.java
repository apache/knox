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
package org.apache.knox.gateway.topology.discovery.cm.model;

import com.cloudera.api.swagger.client.ApiClient;
import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.DiscoveryApiClient;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractServiceModelGenerator implements ServiceModelGenerator {

  private DiscoveryApiClient client;

  @Override
  public void setApiClient(DiscoveryApiClient client) {
    this.client = client;
  }

  protected ApiClient getClient() {
    return client;
  }

  protected String getServiceConfigValue(ApiServiceConfig serviceConfig, String key) {
    return getConfigValue(key, serviceConfig.getItems());
  }

  protected String getRoleConfigValue(ApiConfigList roleConfig, String key) {
    return getConfigValue(key, roleConfig.getItems());
  }

  protected String getConfigValue(String key, List<ApiConfig> items) {
    for (ApiConfig config : items) {
      if (key.equals(config.getName())) {
        String value = config.getValue();
        if (value != null) {
          return value;
        }
        return config.getDefault();
      }
    }
    return null;
  }

  protected String getSafetyValveValue(String safetyValve, String key) {
    String val = null;
    if (safetyValve != null) {
      Pattern p = Pattern.compile("<property><name>" + key + "</name><value>(.*?)</value></property>");
      Matcher m = p.matcher(safetyValve.replaceAll("\\s",""));
      if (m.find()) {
        val = m.group(1);
      }
    }
    return val;
  }

  protected ServiceModel createServiceModel(final String url) {
    return new ServiceModel(getModelType(), getService(), getServiceType(), getRoleType(), url);
  }

}
