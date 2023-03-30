/*
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
package org.apache.knox.gateway.shell.util;

import java.io.File;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * Provides useful helper methods related to gateway client trust store
 * information.
 */
public class ClientTrustStoreHelper {

  private static final String DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_DIR = System.getProperty("user.home");
  private static final String DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_FILENAME = "gateway-client-trust.jks";
  private static final String DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_PASSWORD = "changeit";

  private static final String ENV_GATEWAY_CLIENT_TRUSTSTORE_DIR = "GATEWAY_CLIENT_TRUSTSTORE_DIR";
  private static final String ENV_GATEWAY_CLIENT_TRUSTSTORE_FILENAME = "GATEWAY_CLIENT_TRUSTSTORE_FILENAME";
  private static final String ENV_GATEWAY_CLIENT_TRUSTSTORE_PASSWORD = "GATEWAY_CLIENT_TRUSTSTORE_PASS";
  private static final String ENV_GATEWAY_CLIENT_TRUSTSTORE_TYPE = "GATEWAY_CLIENT_TRUSTSTORE_TYPE";

  public static File getClientTrustStoreFile() {
    final String truststoreDir = fetchTrustStoreAttribute(ENV_GATEWAY_CLIENT_TRUSTSTORE_DIR, DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_DIR);
    final String truststoreFileName = fetchTrustStoreAttribute(ENV_GATEWAY_CLIENT_TRUSTSTORE_FILENAME, DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_FILENAME);
    return Paths.get(truststoreDir, truststoreFileName).toFile();
  }

  public static String getClientTrustStoreFilePassword() {
    return fetchTrustStoreAttribute(ENV_GATEWAY_CLIENT_TRUSTSTORE_PASSWORD, DEFAULT_GATEWAY_CLIENT_TRUSTSTORE_PASSWORD);
  }

  public static String getClientTrustStoreType() {
    return fetchTrustStoreAttribute(ENV_GATEWAY_CLIENT_TRUSTSTORE_TYPE, KeyStore.getDefaultType());
  }

  private static String fetchTrustStoreAttribute(String environmentVariableName, String defaultValue) {
    final String trustStoreAttribute = System.getenv(environmentVariableName);
    return trustStoreAttribute == null ? defaultValue : trustStoreAttribute;
  }
}
