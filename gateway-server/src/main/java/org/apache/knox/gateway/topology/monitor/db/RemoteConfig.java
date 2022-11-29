/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor.db;

import java.time.Instant;

public class RemoteConfig {
  private final String name;
  private final String content;
  private final Instant lastModified;
  private final boolean deleted;

  public RemoteConfig(String name, String content, Instant lastModified) {
    this(name, content, lastModified, false);
  }

  public RemoteConfig(String name, String content, Instant lastModified, boolean deleted) {
    this.name = name;
    this.content = content;
    this.lastModified = lastModified;
    this.deleted = deleted;
  }

  public String getName() {
    return name;
  }

  public String getContent() {
    return content;
  }

  public Instant getLastModified() {
    return lastModified;
  }

  public boolean isDeleted() {
    return deleted;
  }
}
