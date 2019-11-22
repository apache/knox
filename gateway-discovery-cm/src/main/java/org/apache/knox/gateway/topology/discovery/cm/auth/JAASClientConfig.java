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
package org.apache.knox.gateway.topology.discovery.cm.auth;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.net.URL;

class JAASClientConfig extends Configuration {

  private static final Configuration baseConfig = Configuration.getConfiguration();

  private Configuration configFile;

  JAASClientConfig(URL configFileURL) throws Exception {
    if (configFileURL != null) {
      this.configFile = ConfigurationFactory.create(configFileURL.toURI());
    }
  }

  @Override
  public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
    AppConfigurationEntry[] result = null;

    // Try the config file if it exists
    if (configFile != null) {
      result = configFile.getAppConfigurationEntry(name);
    }

    // If the entry isn't there, delegate to the base configuration
    if (result == null) {
      result = baseConfig.getAppConfigurationEntry(name);
    }

    return result;
  }
}
