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
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@XmlType(name = "dispatch")
public class CustomDispatch {

  private String contributorName;

  private String haContributorName;

  private String className;

  private String haClassName;

  private String httpClientFactory;

  private boolean useTwoWaySsl;

  @XmlElement(name = "param")
  private List<XMLParam> params = new ArrayList<>();

  @XmlAttribute(name = "contributor-name")
  public String getContributorName() {
    return contributorName;
  }

  public void setContributorName(String contributorName) {
    this.contributorName = contributorName;
  }

  @XmlAttribute(name = "ha-contributor-name")
  public String getHaContributorName() {
    return haContributorName;
  }

  public void setHaContributorName(String haContributorName) {
    this.haContributorName = haContributorName;
  }

  @XmlAttribute(name = "classname")
  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  @XmlAttribute(name = "ha-classname")
  public String getHaClassName() {
    return haClassName;
  }

  public void setHaClassName(String haContributorClassName) {
    this.haClassName = haContributorClassName;
  }

  @XmlAttribute(name = "http-client-factory")
  public String getHttpClientFactory() {
    return httpClientFactory;
  }

  public void setHttpClientFactory(String httpClientFactory) {
    this.httpClientFactory = httpClientFactory;
  }

  @XmlAttribute(name = "use-two-way-ssl")
  public boolean getUseTwoWaySsl() {
    return useTwoWaySsl;
  }

  public void setUseTwoWaySsl(boolean useTwoWaySsl) {
    this.useTwoWaySsl = useTwoWaySsl;
  }

  /* this is used when we use Apache Commons Digestor bindings, see KnoxFormatXmlTopologyRules.configure() */
  public void setUseTwoWaySsl(String useTwoWaySsl) {
    this.useTwoWaySsl = Boolean.parseBoolean(useTwoWaySsl);
  }

  public void addParam( DispatchParam param ) {
    params.add(new XMLParam(param.getName(), param.getValue()));
  }

  public Map<String, String> getParams() {
    Map<String, String> result = new LinkedHashMap<>();
    if( params != null ) {
      for (XMLParam p : params) {
        result.put(p.name, p.value);
      }
      }
    return result;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  static class XMLParam {
    @XmlElement
    protected String name;

    @XmlElement
    protected String value;

    @SuppressWarnings("unused")
    XMLParam() {

    }

    XMLParam(String name, String value) {
      this.name = name;
      this.value = value;
    }

    String getName() {
      return name;
    }

    String getValue() {
      return value;
    }
  }
}
