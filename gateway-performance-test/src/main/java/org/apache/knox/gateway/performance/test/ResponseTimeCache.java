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
package org.apache.knox.gateway.performance.test;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class ResponseTimeCache {

  private final Queue<Long> acquireResponseTimes = new ConcurrentLinkedQueue<>();
  private final Queue<Long> renewResponseTimes = new ConcurrentLinkedQueue<>();

  public void saveAcquireResponseTime(long getResponseTime) {
    acquireResponseTimes.offer(getResponseTime);
  }

  public void saveRenewResponseTime(long renewResponseTime) {
    renewResponseTimes.offer(renewResponseTime);
  }

  public List<Long> listAcquireResponseTimes() {
    return this.acquireResponseTimes.stream().collect(Collectors.toList());
  }

  public List<Long> listRenewResponseTimes() {
    return this.renewResponseTimes.stream().collect(Collectors.toList());
  }
}
