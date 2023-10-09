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
package org.apache.knox.gateway.service.session;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "sessioninfo")
public class SessionInformation {
  @XmlElement
  private String user;

  @XmlElement
  private String logoutUrl;

  @XmlElement
  private String logoutPageUrl;

  @XmlElement
  private String globalLogoutPageUrl;

  @XmlElement
  private boolean canSeeAllTokens;

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getLogoutUrl() {
    return logoutUrl;
  }

  public void setLogoutUrl(String logoutUrl) {
    this.logoutUrl = logoutUrl;
  }

  public String getLogoutPageUrl() {
    return logoutPageUrl;
  }

  public void setLogoutPageUrl(String logoutPageUrl) {
    this.logoutPageUrl = logoutPageUrl;
  }

  public String getGlobalLogoutPageUrl() {
    return globalLogoutPageUrl;
  }

  public void setGlobalLogoutPageUrl(String globalLogoutPageUrl) {
    this.globalLogoutPageUrl = globalLogoutPageUrl;
  }

  public boolean isCanSeeAllTokens() {
    return canSeeAllTokens;
  }

  public void setCanSeeAllTokens(boolean canSeeAllTokens) {
    this.canSeeAllTokens = canSeeAllTokens;
  }

}
