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
package org.apache.knox.gateway.service.definition;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;

@XmlRootElement(name = "serviceDefinition")
@XmlAccessorType(XmlAccessType.NONE)
public class ServiceDefinitionPair {

  @XmlElement
  private final ServiceDefinition service;

  @XmlElement(name = "rules")
  @XmlJavaTypeAdapter(UrlRewriteRulesDescriptorAdapter.class)
  private final UrlRewriteRulesDescriptor rewriteRules;

  // having a no-argument constructor is required by JAXB
  public ServiceDefinitionPair() {
    this(null, null);
  }

  public ServiceDefinitionPair(ServiceDefinition service, UrlRewriteRulesDescriptor rewriteRules) {
    this.service = service;
    this.rewriteRules = rewriteRules;
  }

  public ServiceDefinition getService() {
    return service;
  }

  public UrlRewriteRulesDescriptor getRewriteRules() {
    return rewriteRules;
  }

  @Override
  public String toString() {
    return service.toString();
  }
}
