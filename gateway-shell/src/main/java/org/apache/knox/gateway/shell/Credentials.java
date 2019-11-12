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
package org.apache.knox.gateway.shell;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class Credentials {
  List<CredentialCollector> collectors = new ArrayList<>();

  public Credentials add(String collectorType, String prompt, String name)
    throws CredentialCollectionException {
    CredentialCollector collector = loadCredentialCollector(collectorType);
    if (collector == null) {
      throw new CredentialCollectionException("Invalid Collector Requested. Type: " + collectorType + " Name: " + name);
    }
    collector.setPrompt(prompt);
    collector.setName(name);
    collectors.add(collector);

    return this;
  }

  public Credentials add(CredentialCollector collector, String prompt, String name)
      throws CredentialCollectionException {
    if (collector == null) {
      throw new CredentialCollectionException("Null CredentialCollector cannot be added.");
    }
    collector.setPrompt(prompt);
    collector.setName(name);
    collectors.add(collector);

    return this;
  }

  public void collect() throws CredentialCollectionException {
    for (CredentialCollector collector : collectors) {
      collector.collect();
    }
  }

  public CredentialCollector get(String name) {
    for (CredentialCollector collector : collectors) {
      if (collector.name().equals(name)) {
        return collector;
      }
    }
    return null;
  }

  private CredentialCollector loadCredentialCollector(String type) {
    ServiceLoader<CredentialCollector> collectorsList = ServiceLoader.load(CredentialCollector.class);
    for (CredentialCollector collector : collectorsList) {
      if (collector.type().equals(type)) {
        return collector;
      }
    }
    return null;
  }
}
