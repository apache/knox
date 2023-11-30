/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.hadoop.xml;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.simple.ProviderConfiguration;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;

class HadoopXmlResourceParserResult {
  private static final HadoopXmlResourceMessages LOG = MessagesFactory.get(HadoopXmlResourceMessages.class);
  final Map<String, ProviderConfiguration> providers;
  final Set<SimpleDescriptor> descriptors;
  private final Set<String> deletedDescriptors;
  private final Set<String> deletedProviders;

  HadoopXmlResourceParserResult() {
    this(Collections.emptyMap(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
  }

  HadoopXmlResourceParserResult(Map<String, ProviderConfiguration> providers, Set<SimpleDescriptor> descriptors,
                                Set<String> deletedDescriptors, Set<String> deletedProviders) {
    this.providers = providers;
    this.descriptors = descriptors;
    this.deletedDescriptors = deletedDescriptors;
    this.deletedProviders = nonReferencedProviders(deletedProviders, descriptors);
  }

  private Set<String> nonReferencedProviders(Set<String> deletedProviders, Set<SimpleDescriptor> descriptors) {
    Set<String> referencedProviders = descriptors.stream()
            .map(SimpleDescriptor::getProviderConfig).collect(Collectors.toSet());
    Set<String> result = new HashSet<>();
    for (String provider : deletedProviders) {
      if (referencedProviders.contains(provider)) {
        LOG.notDeletingReferenceProvider(provider);
      } else {
        result.add(provider);
      }
    }
    return result;
  }

  public Map<String, ProviderConfiguration> getProviders() {
    return Collections.unmodifiableMap(providers);
  }

  public Set<SimpleDescriptor> getDescriptors() {
    return Collections.unmodifiableSet(descriptors);
  }

  public Set<String> getDeletedDescriptors() {
    return deletedDescriptors;
  }

  public Set<String> getDeletedProviders() {
    return deletedProviders;
  }
}
