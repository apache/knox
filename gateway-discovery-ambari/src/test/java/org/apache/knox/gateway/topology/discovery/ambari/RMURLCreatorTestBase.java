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

import org.easymock.EasyMock;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

abstract class RMURLCreatorTestBase {

  abstract String getTargetService();


  abstract ServiceURLCreator getServiceURLCreator(AmbariCluster cluster);


  String doTestCreateSingleURL(final String httpPolicy, final String httpAddress, final String httpsAddress) {
    AmbariCluster.ServiceConfiguration rmSvcConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);

    Map<String, String> configProps = new HashMap<>();
    configProps.put("yarn.http.policy", httpPolicy);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTP, httpAddress);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTPS, httpsAddress);

    EasyMock.expect(rmSvcConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(rmSvcConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration(ResourceManagerURLCreatorBase.CONFIG_SERVICE,
                                                    ResourceManagerURLCreatorBase.CONFIG_TYPE))
            .andReturn(rmSvcConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    List<String> urls = doTestCreateURLs(cluster);
    assertEquals(1, urls.size());
    return urls.get(0);
  }


  List<String> doTestCreateHAURLs(final String httpPolicy,
                                          final String activeHttpAddress,
                                          final String stdbyHttpAddress,
                                          final String activeHttpsAddress,
                                          final String stdbyHttpsAddress) {
    AmbariCluster.ServiceConfiguration rmSvcConfig = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);

    Map<String, String> configProps = new HashMap<>();
    configProps.put("yarn.http.policy", httpPolicy);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTP, activeHttpAddress);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTPS, activeHttpsAddress);
    configProps.put("yarn.resourcemanager.ha.enabled", "true");
    configProps.put("yarn.resourcemanager.ha.rm-ids", "rmOne,rm2");
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTP  + ".rmOne", activeHttpAddress);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTP  + ".rm2",   stdbyHttpAddress);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTPS + ".rmOne", stdbyHttpsAddress);
    configProps.put(ResourceManagerURLCreatorBase.WEBAPP_ADDRESS_HTTPS + ".rm2",   activeHttpsAddress);

    EasyMock.expect(rmSvcConfig.getProperties()).andReturn(configProps).anyTimes();
    EasyMock.replay(rmSvcConfig);

    AmbariCluster cluster = EasyMock.createNiceMock(AmbariCluster.class);
    EasyMock.expect(cluster.getServiceConfiguration(ResourceManagerURLCreatorBase.CONFIG_SERVICE,
                                                    ResourceManagerURLCreatorBase.CONFIG_TYPE))
            .andReturn(rmSvcConfig)
            .anyTimes();
    EasyMock.replay(cluster);

    List<String> urls = doTestCreateURLs(cluster);
    assertEquals(2, urls.size());
    return urls;
  }


  private List<String> doTestCreateURLs(AmbariCluster cluster) {
    ServiceURLCreator rmc = getServiceURLCreator(cluster);
    List<String> urls = rmc.create(getTargetService(), Collections.emptyMap());
    assertNotNull(urls);
    return urls;
  }

}
