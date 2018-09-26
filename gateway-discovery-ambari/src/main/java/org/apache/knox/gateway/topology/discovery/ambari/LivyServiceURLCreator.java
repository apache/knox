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

public class LivyServiceURLCreator extends SparkCommonServiceURLCreator {

  private static final String RESOURCE_ROLE = "LIVYSERVER";

  @Override
  public void init(AmbariCluster cluster) {
    super.init(cluster);
    primaryComponentName   = "LIVY_SERVER";
    secondaryComponentName = "LIVY2_SERVER";
    portConfigProperty     = "livy.server.port";
  }

  @Override
  public String getTargetService() {
    return RESOURCE_ROLE;
  }

  @Override
  boolean isSSL(AmbariComponent comp) {
    String keystore = comp.getConfigProperty("livy.keystore");
    return (keystore != null && !keystore.isEmpty());
  }

}
