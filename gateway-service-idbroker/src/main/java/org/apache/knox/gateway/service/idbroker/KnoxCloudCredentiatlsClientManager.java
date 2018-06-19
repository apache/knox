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

public class KnoxCloudCredentiatlsClientManager implements KnoxCloudCredentialsClient {
  
  private static final String CLOUD_CLIENT_PROVIDER = "cloud.client.provider";
  private KnoxCloudCredentialsClient delegate = null;

  @Override
  public Object getCredentials() {
    return delegate.getCredentials();
  }

  @Override
  public KnoxCloudPolicyProvider getPolicyProvider() {
    return delegate.getPolicyProvider();
  }

  @Override
  public void setPolicyProvider(KnoxCloudPolicyProvider policyProvider) {
    delegate.setPolicyProvider(policyProvider);
  }

  @Override
  public String getName() {
    return delegate.getName();
  }

  @Override
  public void init(Properties context) {
    try {
      delegate = loadDelegate(context.getProperty(CLOUD_CLIENT_PROVIDER));
    }
    catch (IdentityBrokerConfigException e) {
      e.printStackTrace();
    }
  }

  public KnoxCloudCredentialsClient loadDelegate(String name) throws IdentityBrokerConfigException {
    KnoxCloudCredentialsClient delegate = null;
    ServiceLoader<KnoxCloudCredentialsClient> loader = ServiceLoader.load(KnoxCloudCredentialsClient.class);
    Iterator<KnoxCloudCredentialsClient> iterator = loader.iterator();
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
