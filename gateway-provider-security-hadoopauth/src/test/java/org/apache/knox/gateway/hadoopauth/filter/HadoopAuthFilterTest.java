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
package org.apache.knox.gateway.hadoopauth.filter;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class HadoopAuthFilterTest {
  @Test
  public void testHadoopAuthFilterAliases() throws Exception {
    String aliasKey = "signature.secret";
    String aliasConfigKey = "${ALIAS=" + aliasKey + "}";
    String aliasValue = "password";

    Topology topology = new Topology();
    topology.setName("Sample");

    DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();
    EasyMock.replay(context);

    String clusterName = context.getTopology().getName();

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getPasswordFromAliasForCluster(clusterName, aliasKey))
        .andReturn(aliasValue.toCharArray()).anyTimes();
    EasyMock.replay(as);
    DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(as);

    GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.CRYPTO_SERVICE)).andReturn(cryptoService).anyTimes();

    HadoopAuthFilter hadoopAuthFilter = new HadoopAuthFilter();

    String configPrefix = "hadoop.auth.config.";

    Properties props = new Properties();
    props.put("clusterName", clusterName);
    props.put(configPrefix + "signature.secret", aliasConfigKey);
    props.put(configPrefix + "test", "abc");

    FilterConfig filterConfig = new HadoopAuthTestFilterConfig(props);
    Properties configuration = hadoopAuthFilter.getConfiguration(as, configPrefix, filterConfig);
    assertEquals(aliasValue, configuration.getProperty(aliasKey));
    assertEquals("abc", configuration.getProperty("test"));
  }

  private static class HadoopAuthTestFilterConfig implements FilterConfig {
    Properties props;

    HadoopAuthTestFilterConfig(Properties props) {
      this.props = props;
    }

    @Override
    public String getFilterName() {
      return null;
    }

    @Override
    public ServletContext getServletContext() {
      return null;
    }

    @Override
    public String getInitParameter(String name) {
      return props.getProperty(name, null);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return (Enumeration<String>)props.propertyNames();
    }
  }
}
