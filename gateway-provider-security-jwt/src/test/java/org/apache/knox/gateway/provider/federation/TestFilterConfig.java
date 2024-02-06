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

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.Properties;

public class TestFilterConfig implements FilterConfig {
    public static final String TOPOLOGY_NAME_PROP = "test-topology-name";

    private final Properties props;
    private final TokenStateService tokenStateService;

    public TestFilterConfig() {
      this(new Properties());
    }

    public TestFilterConfig(Properties props) {
      this(props, null);
    }

    public TestFilterConfig(Properties props, TokenStateService tokenStateService) {
      this.props = props;
      this.tokenStateService = tokenStateService;
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
        if (tokenStateService != null) {
          final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
          EasyMock.expect(gatewayServices.getService(ServiceType.TOKEN_STATE_SERVICE)).andReturn(tokenStateService).anyTimes();

          // tests with TSS need GatewayConfig and AliasService too for TokenMAC calculation
          final AliasService aliasService  = EasyMock.createNiceMock(AliasService.class);
          try {
            EasyMock.expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn("supersecretpassword".toCharArray()).anyTimes();
          } catch (AliasServiceException e) {
            // NOP - if the mock initialization failed there will be errors down the line
          }
          EasyMock.expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();

          final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
          EasyMock.expect(gatewayConfig.getKnoxTokenHashAlgorithm()).andReturn(HmacAlgorithms.HMAC_SHA_256.getName()).anyTimes();
          EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).anyTimes();

          EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).anyTimes();
          EasyMock.replay(gatewayConfig, gatewayServices, aliasService);
        }
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
