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
package org.apache.knox.gateway.service.idbroker;

import java.util.Iterator;
import java.util.Properties;
import java.util.ServiceLoader;

import javax.security.auth.Subject;

public class KnoxPolicyProviderManager implements KnoxCloudPolicyProvider {

  private static final String DEFAULT_CLOUD_POLICY_CONFIG_PROVIDER = "default";
  private static final String CLOUD_POLICY_CONFIG_PROVIDER = "cloud.policy.config.provider";

  private Properties properties = null;
  private KnoxCloudPolicyProvider delegate = null;

  @Override
  public void init(Properties context) {
    properties = context;
    try {
      delegate = loadDelegate(context.getProperty(CLOUD_POLICY_CONFIG_PROVIDER));
      delegate.init(context);
    }
    catch (IdentityBrokerConfigException e) {
      e.printStackTrace();
    }
  }

  @Override
  public String getName() {
    return properties.getProperty(CLOUD_POLICY_CONFIG_PROVIDER,
        DEFAULT_CLOUD_POLICY_CONFIG_PROVIDER);
  }

  @Override
  public String buildPolicy(String username, Subject subject) {
    return delegate.buildPolicy(username, subject);
  }

  public KnoxCloudPolicyProvider loadDelegate(String name) throws IdentityBrokerConfigException {
    KnoxCloudPolicyProvider delegate = null;
    ServiceLoader<KnoxCloudPolicyProvider> loader = ServiceLoader.load(KnoxCloudPolicyProvider.class);
    Iterator<KnoxCloudPolicyProvider> iterator = loader.iterator();
    while(iterator.hasNext()) {
      delegate = iterator.next();
      if (name.equals(delegate.getName())) {
        break;
      }
    }
    if (delegate == null) {
      throw new IdentityBrokerConfigException(name);
    }
    return delegate;
  }
}
