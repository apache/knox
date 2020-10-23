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
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HaDescriptorFactoryTest {

   @Test
   public void testCreateDescriptor() {
      assertNotNull(HaDescriptorFactory.createDescriptor());
   }

   @Test
   public void testCreateServiceConfig() {
      HaServiceConfig serviceConfig = HaDescriptorFactory.createServiceConfig("foo", "enabled=true;maxFailoverAttempts=42;failoverSleep=50");
      assertNotNull(serviceConfig);
      assertTrue(serviceConfig.isEnabled());
      assertEquals("foo", serviceConfig.getServiceName());
      assertEquals(42, serviceConfig.getMaxFailoverAttempts());
      assertEquals(50, serviceConfig.getFailoverSleep());

      serviceConfig = HaDescriptorFactory.createServiceConfig("bar", "false", "3", "1000", null, null, null, null, null, null);
      assertNotNull(serviceConfig);
      assertFalse(serviceConfig.isEnabled());
      assertEquals("bar", serviceConfig.getServiceName());
      assertEquals(3, serviceConfig.getMaxFailoverAttempts());
      assertEquals(1000, serviceConfig.getFailoverSleep());
   }

  @Test
  public void testCreateServiceConfigActive() {
    HaServiceConfig serviceConfig = HaDescriptorFactory.createServiceConfig("foo", "enableStickySession=true;enabled=true;maxFailoverAttempts=42;failoverSleep=50;maxRetryAttempts=1;retrySleep=1000");
    assertNotNull(serviceConfig);
    assertTrue(serviceConfig.isEnabled());
    assertEquals("foo", serviceConfig.getServiceName());
    assertEquals(42, serviceConfig.getMaxFailoverAttempts());
    assertEquals(50, serviceConfig.getFailoverSleep());
    assertTrue(serviceConfig.isStickySessionEnabled());
    assertEquals(HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME, serviceConfig.getStickySessionCookieName());

    serviceConfig = HaDescriptorFactory.createServiceConfig("foo", "enableStickySession=true;enabled=true;maxFailoverAttempts=42;failoverSleep=50;maxRetryAttempts=1;retrySleep=1000;stickySessionCookieName=abc");
    assertNotNull(serviceConfig);
    assertTrue(serviceConfig.isEnabled());
    assertEquals("foo", serviceConfig.getServiceName());
    assertEquals(42, serviceConfig.getMaxFailoverAttempts());
    assertEquals(50, serviceConfig.getFailoverSleep());
    assertTrue(serviceConfig.isStickySessionEnabled());
    assertEquals("abc", serviceConfig.getStickySessionCookieName());

    serviceConfig = HaDescriptorFactory.createServiceConfig( "bar", "false", "3", "1000", null, null, null, "true", null, null);
    assertNotNull(serviceConfig);
    assertFalse(serviceConfig.isEnabled());
    assertEquals("bar", serviceConfig.getServiceName());
    assertEquals(3, serviceConfig.getMaxFailoverAttempts());
    assertEquals(1000, serviceConfig.getFailoverSleep());
    assertTrue(serviceConfig.isStickySessionEnabled());
    assertEquals(HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME, serviceConfig.getStickySessionCookieName());

    serviceConfig = HaDescriptorFactory.createServiceConfig( "knox", "false", "4", "3000", null, null, null, null, null, null);
    assertNotNull(serviceConfig);
    assertFalse(serviceConfig.isEnabled());
    assertEquals("knox", serviceConfig.getServiceName());
    assertEquals(4, serviceConfig.getMaxFailoverAttempts());
    assertEquals(3000, serviceConfig.getFailoverSleep());
    assertFalse(serviceConfig.isStickySessionEnabled());
    assertEquals(HaServiceConfigConstants.DEFAULT_STICKY_SESSION_COOKIE_NAME, serviceConfig.getStickySessionCookieName());

    serviceConfig = HaDescriptorFactory.createServiceConfig( "bar", "false", "3", "1000", null, null, null, "true", "abc", null);
    assertNotNull(serviceConfig);
    assertFalse(serviceConfig.isEnabled());
    assertEquals("bar", serviceConfig.getServiceName());
    assertEquals(3, serviceConfig.getMaxFailoverAttempts());
    assertEquals(1000, serviceConfig.getFailoverSleep());
    assertTrue(serviceConfig.isStickySessionEnabled());
    assertEquals("abc", serviceConfig.getStickySessionCookieName());

    serviceConfig = HaDescriptorFactory.createServiceConfig( "bar", "false", "3", "1000", null, null, "true", null, "abc", "true");
    assertNotNull(serviceConfig);
    assertFalse(serviceConfig.isEnabled());
    assertEquals("bar", serviceConfig.getServiceName());
    assertEquals(3, serviceConfig.getMaxFailoverAttempts());
    assertEquals(1000, serviceConfig.getFailoverSleep());
    assertFalse(serviceConfig.isStickySessionEnabled());
    assertTrue(serviceConfig.isLoadBalancingEnabled());
    assertTrue(serviceConfig.isNoFallbackEnabled());
    assertEquals("abc", serviceConfig.getStickySessionCookieName());
  }
}
