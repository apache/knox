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

public class SparkHistoryUIServiceURLCreator extends SparkCommonServiceURLCreator {

  private static final String RESOURCE_ROLE = "SPARKHISTORYUI";

  private static final String SSL_FLAG_PRIMARY   = "spark.ssl.historyServer.enabled";
  private static final String SSL_FLAG_SECONDARY = "spark.ssl.enabled";

  private static final String SSL_PORT_PROPERTY = "spark.ssl.historyServer.port";

  private static final int SSL_PORT_OFFSET = 400;

  @Override
  public void init(AmbariCluster cluster) {
    super.init(cluster);
    primaryComponentName   = "SPARK_JOBHISTORYSERVER";
    secondaryComponentName = "SPARK2_JOBHISTORYSERVER";
    portConfigProperty     = "spark.history.ui.port";
  }

  @Override
  public String getTargetService() {
    return RESOURCE_ROLE;
  }


  @Override
  String getPort(AmbariComponent comp) {
    String port;

    if (isSSL(comp)) {
      String sslPort = comp.getConfigProperty(SSL_PORT_PROPERTY);
      if (sslPort == null || sslPort.isEmpty()) {
        int p = Integer.parseInt(comp.getConfigProperty(portConfigProperty)) + SSL_PORT_OFFSET;
        sslPort = String.valueOf(p);
      }
      port = sslPort;
    } else {
      port = comp.getConfigProperty(portConfigProperty);
    }

    return port;
  }

  @Override
  boolean isSSL(AmbariComponent comp) {
    return Boolean.valueOf(comp.getConfigProperty(SSL_FLAG_PRIMARY)) || Boolean.valueOf(comp.getConfigProperty(SSL_FLAG_SECONDARY));
  }

}
