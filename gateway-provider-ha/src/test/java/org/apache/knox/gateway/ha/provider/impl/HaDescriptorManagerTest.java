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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.the;

public class HaDescriptorManagerTest {
   @Test
   public void testDescriptorLoad() throws IOException {
      String xml = "<ha><service name='foo' maxFailoverAttempts='42' failoverSleep='4000' enabled='false'/>" +
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
      assertFalse(config.isEnabled());
      assertFalse(config.isStickySessionEnabled());
      assertFalse(config.isNoFallbackEnabled());
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
      assertEquals(HaServiceConfigConstants.DEFAULT_ENABLED, config.isEnabled());
      assertEquals(HaServiceConfigConstants.DEFAULT_LOAD_BALANCING_ENABLED, config.isLoadBalancingEnabled());
      assertEquals(HaServiceConfigConstants.DEFAULT_STICKY_SESSIONS_ENABLED, config.isStickySessionEnabled());
     assertEquals(HaServiceConfigConstants.DEFAULT_NO_FALLBACK_ENABLED, config.isNoFallbackEnabled());
   }

   @Test
   public void testDescriptorStoring() throws IOException {
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("foo", "false", "42", "1000", "foo:2181,bar:2181", "hiveserver2", null, null, null, null));
      descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("bar", "true", "3", "5000", null, null, null, null, null, null));
      StringWriter writer = new StringWriter();
      HaDescriptorManager.store(descriptor, writer);
      String xml = writer.toString();
      assertThat( the( xml ), hasXPath( "/ha//service[@enabled='false' and @failoverSleep='1000' and @maxFailoverAttempts='42' and @name='foo' and @zookeeperEnsemble='foo:2181,bar:2181' and @zookeeperNamespace='hiveserver2']" ) );
      assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='bar']" ) );
   }

  @Test
  public void testDescriptorStoringStickySessionCookie() throws IOException {
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("foo", "false", "42", "1000", "foo:2181,bar:2181", "hiveserver2", null, "true", null, null));
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("bar", "true", "3", "5000", null, null, null, "true", null, null));
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("abc", "true", "3", "5000", null, null, null, "true", "abc", null));
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("xyz", "true", "3", "5000", null, null, null, "true", "xyz", "true"));

    StringWriter writer = new StringWriter();
    HaDescriptorManager.store(descriptor, writer);
    String xml = writer.toString();
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='false' and @failoverSleep='1000' and @maxFailoverAttempts='42' and @name='foo' and @zookeeperEnsemble='foo:2181,bar:2181' and @zookeeperNamespace='hiveserver2' and @enableStickySession='true']" ) );
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='bar' and @enableStickySession='true']" ) );
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='abc' and @enableStickySession='true' and @stickySessionCookieName='abc']" ) );
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='xyz' and @enableStickySession='true' and @stickySessionCookieName='xyz' and @noFallback='true']" ) );
  }

  @Test
  public void testDescriptorStoringLoadBalancerConfig() throws IOException {
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("foo", "false", "42", "1000", "foo:2181,bar:2181", "hiveserver2", "true", "false", null, null));
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("bar", "true", "3", "5000", null, null, "true", null, null, null));
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig("abc", "true", "3", "5000", null, null, null, "true", "abc", null));
    StringWriter writer = new StringWriter();
    HaDescriptorManager.store(descriptor, writer);
    String xml = writer.toString();
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='false' and @failoverSleep='1000' and @maxFailoverAttempts='42' and @name='foo' and @zookeeperEnsemble='foo:2181,bar:2181' and @zookeeperNamespace='hiveserver2' and @enableLoadBalancing='true' and @enableStickySession='false']" ) );
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='bar' and @enableLoadBalancing='true' and @enableStickySession='false']" ) );
    assertThat( the( xml ), hasXPath( "/ha//service[@enabled='true' and @failoverSleep='5000' and @maxFailoverAttempts='3' and @name='abc' and @enableLoadBalancing='false' and @enableStickySession='true' and @stickySessionCookieName='abc']" ) );
  }
}
