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
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.services.GatewayServices;
import org.easymock.EasyMock;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.Properties;

public class TestFilterConfig implements FilterConfig {
    public static final String TOPOLOGY_NAME_PROP = "test-topology-name";

    Properties props;

    public TestFilterConfig() {
        this.props = new Properties();
    }

    public TestFilterConfig(Properties props) {
        this.props = props;
    }

    @Override
    public String getFilterName() {
        return null;
    }

    @Override
    public ServletContext getServletContext() {
        String topologyName = props.getProperty(TOPOLOGY_NAME_PROP);
        if (topologyName == null) {
            topologyName = "jwt-test-topology";
        }
        ServletContext context = EasyMock.createNiceMock(ServletContext.class);
        EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(topologyName).anyTimes();
        EasyMock.replay(context);
        return context;
    }

    @Override
    public String getInitParameter(String name) {
        return props.getProperty(name, null);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return null;
    }

}
