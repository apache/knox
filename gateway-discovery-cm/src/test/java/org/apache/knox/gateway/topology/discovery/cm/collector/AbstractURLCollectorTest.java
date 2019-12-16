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
package org.apache.knox.gateway.topology.discovery.cm.collector;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.easymock.EasyMock;

public abstract class AbstractURLCollectorTest {


  protected static ServiceModel createMockServiceModel(final String service,
                                                       final String serviceType,
                                                       final String roleType,
                                                       final String url) {
    ServiceModel sm  = EasyMock.createNiceMock(ServiceModel.class);
    EasyMock.expect(sm.getService()).andReturn(service).anyTimes();
    EasyMock.expect(sm.getServiceType()).andReturn(serviceType).anyTimes();
    EasyMock.expect(sm.getRoleType()).andReturn(roleType).anyTimes();
    EasyMock.expect(sm.getServiceUrl()).andReturn(url).anyTimes();
    EasyMock.replay(sm);
    return sm;
  }



}
