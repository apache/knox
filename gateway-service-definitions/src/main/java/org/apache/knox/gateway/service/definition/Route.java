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

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import java.util.List;

@XmlType(name = "route")
public class Route {

  private String path;

  private List<Rewrite> rewrites;

  private List<Policy> policies;

  private CustomDispatch dispatch;

  @XmlAttribute
  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  @XmlElement(name = "rewrite")
  public List<Rewrite> getRewrites() {
    return rewrites;
  }

  public void setRewrites(List<Rewrite> rewrites) {
    this.rewrites = rewrites;
  }

  @XmlElement(name = "policy")
  @XmlElementWrapper(name = "policies")
  public List<Policy> getPolicies() {
    return policies;
  }

  public void setPolicies(List<Policy> policies) {
    this.policies = policies;
  }

  @XmlElement(name = "dispatch")
  public CustomDispatch getDispatch() {
    return dispatch;
  }

  public void setDispatch(CustomDispatch dispatch) {
    this.dispatch = dispatch;
  }
}
