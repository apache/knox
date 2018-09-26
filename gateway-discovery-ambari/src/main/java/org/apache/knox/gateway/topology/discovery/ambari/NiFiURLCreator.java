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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NiFiURLCreator implements ServiceURLCreator {

  static final String COMPONENT_NAME  = "NIFI_MASTER";
  static final String CONFIG_SERVICE  = "NIFI";
  static final String CONFIG_TYPE     = "nifi-ambari-config";
  static final String CONFIG_TYPE_SSL = "nifi-ambari-ssl-config";

  static final String SSL_ENABLED_PROPERTY = "nifi.node.ssl.isenabled";

  static final String SCHEME_HTTP  = "http";
  static final String SCHEME_HTTPS = "https";

  static final String PORT_PROPERTY     = "nifi.node.port";
  static final String SSL_PORT_PROPERTY = "nifi.node.ssl.port";


  private AmbariCluster cluster = null;

  @Override
  public String getTargetService() {
    return CONFIG_SERVICE ;
  }

  @Override
  public void init(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    AmbariComponent component = cluster.getComponent(COMPONENT_NAME);
    if (component != null) {
      AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration(service, CONFIG_TYPE);
      if (sc != null) {
        Map<String, String> configProps = sc.getProperties();

        boolean isSSLEnabled = isSSLEnabled(service);
        String scheme = isSSLEnabled ? SCHEME_HTTPS : SCHEME_HTTP;
        String port = isSSLEnabled ? configProps.get(SSL_PORT_PROPERTY) : configProps.get(PORT_PROPERTY);

        for (String hostName : component.getHostNames()) {
          urls.add(scheme + "://" + hostName + ":" + port);
        }
      }
    }
    return urls;
  }

  private boolean isSSLEnabled(String service) {
    boolean isSSLEnabled = false;

    AmbariCluster.ServiceConfiguration config = cluster.getServiceConfiguration(service, CONFIG_TYPE_SSL);
    if (config != null) {
      isSSLEnabled = Boolean.valueOf(config.getProperties().getOrDefault(SSL_ENABLED_PROPERTY, "false"));
    }

    return isSSLEnabled;
  }

}
