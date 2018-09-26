/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.the;

public class HaDescriptorManagerTest {

   @Test
   public void testDescriptorLoad() throws IOException {
      String xml = "<ha><service name='foo' maxFailoverAttempts='42' failoverSleep='4000' maxRetryAttempts='2' retrySleep='2213' enabled='false'/>" +
            "<service name='bar' failoverLimit='3' enabled='true'/></ha>";
      ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
      HaDescriptor descriptor = HaDescriptorManager.load(inputStream);
      assertNotNull(descriptor);
      assertEquals(1, descriptor.getEnabledServiceNames().size());
      HaServiceConfig config = descriptor.getServiceConfig("foo");
      assertNotNull(config);
      assertEquals("foo", config.getServiceName());
      assertEquals(42, config.getMaxFailoverAttempts());
      assertEquals(4000, config.getFailoverSleep());
      assertEquals(2, config.getMaxRetryAttempts());
      assertEquals(2213, config.getRetrySleep());
      assertFalse(config.isEnabled());
      config = descriptor.getServiceConfig("bar");
      assertTrue(config.isEnabled());
   }

   @Test
   public void testDescriptorDefaults() throws IOException {
      String xml = "<ha><service name='foo'/></ha>";
      ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
      HaDescriptor descriptor = HaDescriptorManager.load(inputStream);
      assertNotNull(descriptor);
      assertEquals(1, descriptor.getEnabledServiceNames().size());
      HaServiceConfig config = descriptor.getServiceConfig("foo");
      assertNotNull(config);
      assertEquals("foo", config.getServiceName());
      assertEquals(HaServiceConfigConstants.DEFAULT_MAX_FAILOVER_ATTEMPTS, config.getMaxFailoverAttempts());
      assertEquals(HaServiceConfigConstants.DEFAULT_FAILOVER_SLEEP, config.getFailoverSleep());
      assertEquals(HaServiceConfigConstants.DEFAULT_MAX_RETRY_ATTEMPTS, config.getMaxRetryAttempts());
      assertEquals(HaServiceConfigConstants.DEFAULT_RETRY_SLEEP, config.getRetrySleep());
      assertEquals(HaServiceConfigConstants.DEFAULT_ENABLED, config.isEnabled());
   }

   @Test
   public void testDescriptorStoring() throws IOException {
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("foo", "false", "42", "1000", "3", "3000", "foo:2181,bar:2181", "hiveserver2"));
      descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("bar", "true", "3", "5000", "5", "8000", null, null));
      StringWriter writer = new StringWriter();
      HaDescriptorManager.store(descriptor, writer);
      String descriptorXml = writer.toString();
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
            "<ha>\n" +
            "  <service enabled=\"false\" failoverSleep=\"1000\" maxFailoverAttempts=\"42\" maxRetryAttempts=\"3\" name=\"foo\" retrySleep=\"3000\" zookeeperEnsemble=\"foo:2181,bar:2181\" zookeeperNamespace=\"hiveserver2\"/>\n" +
            "  <service enabled=\"true\" failoverSleep=\"5000\" maxFailoverAttempts=\"3\" maxRetryAttempts=\"5\" name=\"bar\" retrySleep=\"8000\"/>\n" +
            "</ha>\n";
      assertThat( the( xml ), hasXPath( "/ha/service[@enabled='false' and @failoverSleep='1000' and @maxFailoverAttempts='42' and @maxRetryAttempts='3' and @name='foo' and @retrySleep='3000' and @zookeeperEnsemble='foo:2181,bar:2181' and @zookeeperNamespace='hiveserver2']" ) );
      assertThat( the( xml ), hasXPath( "/ha/service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @maxRetryAttempts='5' and @name='bar' and @retrySleep='8000']" ) );
   }


}
