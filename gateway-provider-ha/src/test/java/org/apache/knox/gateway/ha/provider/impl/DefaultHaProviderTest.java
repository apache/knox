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

import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.junit.Test;

import java.util.ArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIn.in;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultHaProviderTest {

   @Test
   public void testDescriptor() {
      try {
         new DefaultHaProvider(null);
         fail("provider construction should have failed with null descriptor");
      } catch (IllegalArgumentException e) {
        // Expected exception
        assertEquals("Descriptor can not be null", e.getMessage());
      }
      HaDescriptor descriptor = new DefaultHaDescriptor();
      HaProvider provider = new DefaultHaProvider(descriptor);
      assertNotNull(provider.getHaDescriptor());
      descriptor.addServiceConfig(new DefaultHaServiceConfig("foo"));
      assertTrue(provider.isHaEnabled("foo"));
   }

   @Test
   public void testAddingService() {
      HaDescriptor descriptor = new DefaultHaDescriptor();
      HaProvider provider = new DefaultHaProvider(descriptor);
      ArrayList<String> urls = new ArrayList<>();
      urls.add("http://host1");
      urls.add("http://host2");
      provider.addHaService("foo", urls);
      assertNull(provider.getActiveURL("bar"));
      String url = provider.getActiveURL("foo");
      assertNotNull(url);
      assertThat(url, is(in(urls)));
   }

   @Test
   public void testActiveUrl() {
      HaDescriptor descriptor = new DefaultHaDescriptor();
      HaProvider provider = new DefaultHaProvider(descriptor);
      ArrayList<String> urls = new ArrayList<>();
      String url1 = "http://host1";
      urls.add(url1);
      String url2 = "http://host2";
      urls.add(url2);
      String url3 = "http://host3";
      urls.add(url3);
      String serviceName = "foo";
      provider.addHaService(serviceName, urls);
      assertEquals(url1, provider.getActiveURL(serviceName));
      provider.markFailedURL(serviceName, url1);
      assertEquals(url2, provider.getActiveURL(serviceName));
      provider.markFailedURL(serviceName, url2);
      assertEquals(url3, provider.getActiveURL(serviceName));
      provider.markFailedURL(serviceName, url3);
      assertEquals(url1, provider.getActiveURL(serviceName));
      provider.setActiveURL(serviceName, url3);
      assertEquals(url3, provider.getActiveURL(serviceName));
      provider.setActiveURL(serviceName, url2);
      assertEquals(url2, provider.getActiveURL(serviceName));
   }
}
