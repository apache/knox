/**
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
package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.config.GatewayConfig;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class GatewayTestConfig implements GatewayConfig {

  private String gatewayHomeDir = "gateway";
  private String hadoopConfDir = "hadoop";
  private String gatewayHost = "localhost";
  private int gatewayPort = 0;
  private String gatewayPath = "gateway";
  private String clusterConfDir = "clusters";

  @Override
  public String getGatewayHomeDir() {
    return gatewayHomeDir;
  }

  public void setGatewayHomeDir( String gatewayHomeDir ) {
    this.gatewayHomeDir = gatewayHomeDir;
  }

  @Override
  public String getHadoopConfDir() {
    return hadoopConfDir;
  }

  public void setHadoopConfDir( String hadoopConfDir ) {
    this.hadoopConfDir = hadoopConfDir;
  }

  @Override
  public String getGatewayHost() {
    return gatewayHost;
  }

  public void setGatewayHost( String gatewayHost ) {
    this.gatewayHost = gatewayHost;
  }

  @Override
  public int getGatewayPort() {
    return gatewayPort;
  }

  public void setGatewayPort( int gatewayPort ) {
    this.gatewayPort = gatewayPort;
  }

  @Override
  public String getGatewayPath() {
    return gatewayPath;
  }

  public void setGatewayPath( String gatewayPath ) {
    this.gatewayPath = gatewayPath;
  }

  @Override
  public String getClusterConfDir() {
    return clusterConfDir;
  }

  public void setClusterConfDir( String clusterConfDir ) {
    this.clusterConfDir = clusterConfDir;
  }

  @Override
  public InetSocketAddress getGatewayAddress() throws UnknownHostException {
    return new InetSocketAddress( getGatewayHost(), getGatewayPort() );
  }

}
