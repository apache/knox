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

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DefaultURLManagerTest {

   @Test
   public void testActiveURLManagement() {
      ArrayList<String> urls = new ArrayList<>();
      String url1 = "http://host1";
      urls.add(url1);
      String url2 = "http://host2";
      urls.add(url2);
      DefaultURLManager manager = new DefaultURLManager();
      manager.setURLs(urls);
      assertTrue(manager.getURLs().containsAll(urls));
      assertEquals(url1, manager.getActiveURL());
      manager.markFailed(url1);
      assertEquals(url2, manager.getActiveURL());
      manager.markFailed(url2);
      assertEquals(url1, manager.getActiveURL());
   }

   /**
    * KNOX-1104
    * Verify that a service with HaProvider configuration, but only a single URL does not break the HaProvider.
    */
   @Test
   public void testSingleURLManagement() {
      ArrayList<String> urls = new ArrayList<>();
      String url1 = "http://host1";
      urls.add(url1);
      DefaultURLManager manager = new DefaultURLManager();
      manager.setURLs(urls);
      assertTrue(manager.getURLs().containsAll(urls));
      assertEquals(url1, manager.getActiveURL());
      manager.markFailed(url1);
      assertEquals(url1, manager.getActiveURL());
      manager.markFailed(url1);
      assertEquals(url1, manager.getActiveURL());
   }

   @Test
   public void testMarkingFailedURL() {
      ArrayList<String> urls = new ArrayList<>();
      String url1 = "http://host1:4555";
      urls.add(url1);
      String url2 = "http://host2:1234";
      urls.add(url2);
      String url3 = "http://host1:1234";
      urls.add(url3);
      String url4 = "http://host2:4555";
      urls.add(url4);
      DefaultURLManager manager = new DefaultURLManager();
      manager.setURLs(urls);
      assertTrue(manager.getURLs().containsAll(urls));
      assertEquals(url1, manager.getActiveURL());
      manager.markFailed(url1);
      assertEquals(url2, manager.getActiveURL());
      manager.markFailed(url1);
      assertEquals(url2, manager.getActiveURL());
      manager.markFailed(url3);
      assertEquals(url2, manager.getActiveURL());
      manager.markFailed(url4);
      assertEquals(url2, manager.getActiveURL());
      manager.markFailed(url2);
      assertEquals(url3, manager.getActiveURL());
   }

   /**
    * KNOX-1283
    */
   @Test
   public void testMarkFailedWithEmptyURLs() {
      ArrayList<String> urls = new ArrayList<>();
      DefaultURLManager manager = new DefaultURLManager();
      manager.setURLs(urls);
      assertTrue(manager.getURLs().isEmpty());
      try {
         manager.markFailed("http://localhost:8080");
      } catch (NullPointerException e) {
         fail("Empty URL list should not result in NPE.");
      }
   }

}
