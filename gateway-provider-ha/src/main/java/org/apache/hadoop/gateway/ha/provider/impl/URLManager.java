/**
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
package org.apache.hadoop.gateway.ha.provider.impl;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class URLManager {

   private ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<String>();

   public URLManager(List<String> urls) {
      this.urls.addAll(urls);
   }

   public String getActiveURL() {
      return urls.peek();
   }

   public List<String> getURLs() {
      return Lists.newArrayList(urls.iterator());
   }

   public void setURLs(List<String> urls) {
      if (urls != null) {
         urls.clear();
         urls.addAll(urls);
      }
   }

   public void markFailed(String url) {
      //TODO: check if the url is the one on top
//      if (urls.peek().equals(url)) {
      //put the failed url at the bottom
      String failed = urls.poll();
      urls.offer(failed);
//      }
   }
}
