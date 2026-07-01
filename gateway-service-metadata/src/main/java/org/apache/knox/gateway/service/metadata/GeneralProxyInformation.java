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
package org.apache.knox.gateway.service.metadata;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@XmlRootElement(name = "generalProxyInfo")
@ApiModel(value = "generalProxyInfo", description = "General proxy information holder")
public class GeneralProxyInformation {

  @XmlElement
  @ApiModelProperty(value = "The version of this Knox Gateway")
  private String version;

  @XmlElement
  @ApiModelProperty(value = "The name of the host where this Knox Gateway is running")
  private String hostname;

  @XmlElement
  @ApiModelProperty(value = "The Admin UI URL")
  private String adminUiUrl;

  @XmlElement
  @ApiModelProperty(value = "The Web Shell URL")
  private String webShellUrl;

  @XmlElement
  @ApiModelProperty(value = "The URL referencing the Admin API book in Knox's user guide")
  private String adminApiBookUrl;

  @XmlElement
  @ApiModelProperty(value = "A boolean flag indicating if Knox token management should be enabled on the Knox Home page")
  private String enableTokenManagement = "false";

  @XmlElement
  @ApiModelProperty(value = "A boolean flag indicating whether Webshell UI should be enabled on the Knox Home page")
  private String enableWebshell = "false";

  @XmlElement
  @ApiModelProperty(value = "The truststore type the homepage should offer for download (e.g. 'jks' or 'bcfks')")
  private String truststoreType = "jks";

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public String getAdminUiUrl() {
    return adminUiUrl;
  }

  public void setAdminUiUrl(String adminUiUrl) {
    this.adminUiUrl = adminUiUrl;
  }

  public String getWebShellUrl() {
    return webShellUrl;
  }

  public void setWebShellUrl(String webShellUrl) {
    this.webShellUrl = webShellUrl;
  }


  public String getAdminApiBookUrl() {
    return adminApiBookUrl;
  }

  public void setAdminApiBookUrl(String adminApiBookUrl) {
    this.adminApiBookUrl = adminApiBookUrl;
  }

  public String getEnableTokenManagement() {
    return enableTokenManagement;
  }

  public void setEnableTokenManagement(String enableTokenManagement) {
    this.enableTokenManagement = enableTokenManagement;
  }

  public String getEnableWebshell() {
    return enableWebshell;
  }

  public void setEnableWebshell(String enableWebshell) {
    this.enableWebshell = enableWebshell;
  }

  public String getTruststoreType() {
    return truststoreType;
  }

  public void setTruststoreType(String truststoreType) {
    this.truststoreType = truststoreType;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final GeneralProxyInformation instance = new GeneralProxyInformation();

    private Builder() {}

    public Builder version(String version) {
      instance.setVersion(version);
      return this;
    }

    public Builder hostname(String hostname) {
      instance.setHostname(hostname);
      return this;
    }

    public Builder adminUiUrl(String adminUiUrl) {
      instance.setAdminUiUrl(adminUiUrl);
      return this;
    }

    public Builder webShellUrl(String webShellUrl) {
      instance.setWebShellUrl(webShellUrl);
      return this;
    }

    public Builder adminApiBookUrl(String adminApiBookUrl) {
      instance.setAdminApiBookUrl(adminApiBookUrl);
      return this;
    }

    public Builder enableTokenManagement(boolean enableTokenManagement) {
      instance.setEnableTokenManagement(Boolean.toString(enableTokenManagement));
      return this;
    }

    public Builder enableWebshell(boolean enableWebshell) {
      instance.setEnableWebshell(Boolean.toString(enableWebshell));
      return this;
    }

    public Builder truststoreType(String truststoreType) {
      instance.setTruststoreType(truststoreType);
      return this;
    }

    public GeneralProxyInformation build() {
      return instance;
    }
  }

}
