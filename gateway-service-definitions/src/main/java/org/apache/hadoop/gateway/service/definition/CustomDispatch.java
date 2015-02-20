/**
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
package org.apache.hadoop.gateway.service.definition;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "dispatch")
public class CustomDispatch {

  private String contributorName;

  private String haContributorName;

  private String className;

  private String haClassName;

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
}
