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
package org.apache.hadoop.gateway.config;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public interface GatewayConfig {

  static final String GATEWAY_HOME_VAR = "GATEWAY_HOME";
  
  public static final String HADOOP_KERBEROS_SECURED = "gateway.hadoop.kerberos.secured";
  public static final String KRB5_CONFIG = "java.security.krb5.conf";
  public static final String KRB5_DEBUG = "sun.security.krb5.debug";
  public static final String KRB5_LOGIN_CONFIG = "java.security.auth.login.config";
  public static final String KRB5_USE_SUBJECT_CREDS_ONLY = "javax.security.auth.useSubjectCredsOnly";

  String getGatewayHomeDir();

  String getHadoopConfDir();

  String getGatewayHost();

  int getGatewayPort();

  String getGatewayPath();

  String getDeploymentDir();

  InetSocketAddress getGatewayAddress() throws UnknownHostException;
  
  boolean isSSLEnabled();
  
  boolean isHadoopKerberosSecured();
  
  String getKerberosConfig();
  
  boolean isKerberosDebugEnabled();
  
  String getKerberosLoginConfig();

}
