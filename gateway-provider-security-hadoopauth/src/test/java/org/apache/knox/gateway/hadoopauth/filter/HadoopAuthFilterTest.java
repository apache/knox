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

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.GatewayFilter;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.topology.Topology;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class HadoopAuthFilterTest {
  @Test
  public void testHadoopAuthFilterAliases() throws Exception {
    String aliasKey = "signature.secret";
    String aliasConfigKey = "${ALIAS=" + aliasKey + "}";
    String aliasValue = "password";
    String clusterName = "Sample";

    Topology topology = new Topology();
    topology.setName(clusterName);

    AliasService as = createMock(AliasService.class);
    expect(as.getPasswordFromAliasForCluster(clusterName, aliasKey))
        .andReturn(aliasValue.toCharArray()).atLeastOnce();

    String configPrefix = "hadoop.auth.config.";

    Map<String, String> props = new HashMap<>();
    props.put("clusterName", clusterName);
    props.put(configPrefix + "signature.secret", aliasConfigKey);
    props.put(configPrefix + "test", "abc");

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(anyString()))
        .andAnswer(() -> props.get(getCurrentArguments()[0].toString()))
        .atLeastOnce();
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(props.keySet())).atLeastOnce();

    replay(filterConfig, as);

    HadoopAuthFilter hadoopAuthFilter = new HadoopAuthFilter();

    Properties configuration = hadoopAuthFilter.getConfiguration(as, configPrefix, filterConfig);
    assertEquals(aliasValue, configuration.getProperty(aliasKey));
    assertEquals("abc", configuration.getProperty("test"));

    verify(filterConfig, as);
  }

  @Test
  public void testHadoopAuthFilterIgnoreDoAs() throws Exception {
    Topology topology = new Topology();
    topology.setName("Sample");

    ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter("config.prefix"))
        .andReturn("some.prefix")
        .atLeastOnce();
    expect(filterConfig.getInitParameterNames())
        .andReturn(Collections.enumeration(Collections.singleton(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)))
        .atLeastOnce();
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS))
        .andReturn("Knox, hdfs,TesT") // Spacing and case set on purpose
        .atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn("false").anyTimes();
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    Properties configProperties = createMock(Properties.class);
    expect(configProperties.getProperty("signature.secret.file")).andReturn("signature.secret.file").atLeastOnce();
    expect(configProperties.getProperty(anyString(), anyString())).andAnswer(() -> {
      Object[] args = getCurrentArguments();

      if ("type".equals(args[0])) {
        return "simple"; // This is "simple", rather than "kerberos" to avoid the super class' init logic
      } else {
        return (String) args[1];
      }
    }).atLeastOnce();

    HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class)
        .addMockedMethod("getConfiguration", String.class, FilterConfig.class)
        .withConstructor()
        .createMock();
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig)))
        .andReturn(configProperties)
        .atLeastOnce();

    replay(filterConfig, configProperties, hadoopAuthFilter, servletContext);

    hadoopAuthFilter.init(filterConfig);

    assertTrue(hadoopAuthFilter.ignoreDoAs("knox"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("hdfs"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("test"));
    assertTrue(hadoopAuthFilter.ignoreDoAs("TEST"));
    assertTrue(hadoopAuthFilter.ignoreDoAs(null));
    assertTrue(hadoopAuthFilter.ignoreDoAs(""));
    assertFalse(hadoopAuthFilter.ignoreDoAs("hive"));
    assertFalse(hadoopAuthFilter.ignoreDoAs("HivE"));

    verify(filterConfig, configProperties, hadoopAuthFilter, servletContext);
  }

  @Test
  public void shouldNotUseJwtFilterIfProviderParamIsNotSet() throws Exception {
    testIfJwtSupported(null);
  }

  @Test
  public void shouldNotUseJwtFilterIfProviderParamIsFalse() throws Exception {
    testIfJwtSupported("false");
  }

  @Test
  public void shouldUseJwtFilterIfProviderParamIsTrue() throws Exception {
    testIfJwtSupported("true");
  }

  private HadoopAuthFilter testIfJwtSupported(String supportJwt) throws Exception {
    final GatewayFilter.Holder filterConfig = createMock(GatewayFilter.Holder.class);
    expect(filterConfig.getInitParameterNames()).andReturn(Collections.enumeration(Collections.emptyList()));
    expect(filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS)).andReturn("service").atLeastOnce();
    expect(filterConfig.getInitParameter("config.prefix")).andReturn("some.prefix").atLeastOnce();
    expect(filterConfig.getInitParameter("support.jwt")).andReturn(supportJwt).anyTimes();
    final boolean isJwtSupported = Boolean.parseBoolean(supportJwt);
    if (isJwtSupported) {
      filterConfig.removeParamPrefix("jwt.");
      expectLastCall();
      expect(filterConfig.getInitParameter(JWTFederationFilter.KNOX_TOKEN_AUDIENCES)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.KNOX_TOKEN_QUERY_PARAM_NAME)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.JWKS_URL)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.TOKEN_PRINCIPAL_CLAIM)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(JWTFederationFilter.TOKEN_VERIFICATION_PEM)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(AbstractJWTFilter.JWT_EXPECTED_ISSUER)).andReturn(null).anyTimes();
      expect(filterConfig.getInitParameter(AbstractJWTFilter.JWT_EXPECTED_SIGALG)).andReturn(null).anyTimes();
    }

    final ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute("signer.secret.provider.object")).andReturn(null).atLeastOnce();
    if (isJwtSupported) {
      expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(null).anyTimes();
    }
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();

    final HadoopAuthFilter hadoopAuthFilter = createMockBuilder(HadoopAuthFilter.class).addMockedMethod("getConfiguration", String.class, FilterConfig.class).withConstructor()
        .createMock();
    final Properties config = new Properties();
    config.put("type", "simple");
    expect(hadoopAuthFilter.getConfiguration(eq("some.prefix."), eq(filterConfig))).andReturn(config).atLeastOnce();

    replay(servletContext, filterConfig, hadoopAuthFilter);

    hadoopAuthFilter.init(filterConfig);

    assertEquals(isJwtSupported, hadoopAuthFilter.isJwtSupported());

    return hadoopAuthFilter;
  }
}
