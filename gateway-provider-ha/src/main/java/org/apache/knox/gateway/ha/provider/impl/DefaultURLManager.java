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

import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultURLManager implements URLManager {

  private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

  private final ConcurrentLinkedQueue<String> urls = new ConcurrentLinkedQueue<>();

  @Override
  public boolean supportsConfig(HaServiceConfig config) {
    return true;
  }

  @Override
  public void setConfig(HaServiceConfig config) {
    //no-op
  }

  @Override
  public synchronized String getActiveURL() {
    return urls.peek();
  }

  @Override
  public synchronized void setActiveURL(String url) {
    String top = urls.peek();
    if (top.equalsIgnoreCase(url)) {
      return;
    }
    if (urls.contains(url)) {
      urls.remove(url);
      List<String> remainingList = getURLs();
      urls.clear();
      urls.add(url);
      urls.addAll(remainingList);
    }
  }

  @Override
  public synchronized List<String> getURLs() {
    return new ArrayList<>(urls);
  }

  @Override
  public synchronized void setURLs(List<String> urls) {
    if (urls != null && !urls.isEmpty()) {
      this.urls.clear();
      this.urls.addAll(urls);
    }
  }

  @Override
  public synchronized void markFailed(String url) {
    String top = urls.peek();
    if (top != null) {
      boolean pushToBottom = false;
      URI topUri = URI.create(top);
      URI incomingUri = URI.create(url);
      String topHostPort = topUri.getHost() + ":" + topUri.getPort();
      String incomingHostPort = incomingUri.getHost() + ":" + incomingUri.getPort();
      if (topHostPort.equals(incomingHostPort)) {
        pushToBottom = true;
      }
      //put the failed url at the bottom
      if (pushToBottom) {
        String failed = urls.poll();
        urls.offer(failed);
        LOG.markedFailedUrl(failed, urls.peek());
      }
    }
  }

  @Override
  public synchronized void makeNextActiveURLAvailable() {
    String head = urls.poll();
    urls.offer(head);
  }
}
