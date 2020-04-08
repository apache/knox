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
package org.apache.knox.gateway.service.config.remote.config;

import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryConfig;

import javax.xml.bind.annotation.XmlElement;

class RemoteConfigurationRegistry implements RemoteConfigurationRegistryConfig {

    private String name;
    private String type;
    private String connectionString;
    private String namespace;
    private String authType;
    private String principal;
    private String credentialAlias;
    private String keyTab;
    private boolean useKeyTab;
    private boolean useTicketCache;
    /* If true ensures that the auth scheme used to create znodes is `auth` and not `sasl` */
    private boolean isBackwardsCompatible;

    RemoteConfigurationRegistry() {
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRegistryType(String type) {
        this.type = type;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public void setCredentialAlias(String alias) {
        this.credentialAlias = alias;
    }

    public void setUseTicketCache(boolean useTicketCache) {
        this.useTicketCache = useTicketCache;
    }

    public void setUseKeytab(boolean useKeytab) {
        this.useKeyTab = useKeytab;
    }

    public void setKeytab(String keytab) {
        this.keyTab = keytab;
    }

    public void setBackwardsCompatible(boolean backwardsCompatible) {
        isBackwardsCompatible = backwardsCompatible;
    }

    @Override
    @XmlElement(name="name")
    public String getName() {
        return name;
    }

    @Override
    @XmlElement(name="type")
    public String getRegistryType() {
        return type;
    }

    @Override
    @XmlElement(name="auth-type")
    public String getAuthType() {
        return authType;
    }

    @Override
    @XmlElement(name="principal")
    public String getPrincipal() {
        return principal;
    }

    @Override
    @XmlElement(name="credential-alias")
    public String getCredentialAlias() {
        return credentialAlias;
    }

    @Override
    @XmlElement(name="address")
    public String getConnectionString() {
        return connectionString;
    }

    @Override
    @XmlElement(name="namespace")
    public String getNamespace() {
        return namespace;
    }

    @Override
    @XmlElement(name="use-ticket-cache")
    public boolean isUseTicketCache() {
        return useTicketCache;
    }

    @Override
    @XmlElement(name="use-key-tab")
    public boolean isUseKeyTab() {
        return useKeyTab;
    }


    @Override
    @XmlElement(name="keytab")
    public String getKeytab() {
        return keyTab;
    }

    @Override
    public boolean isSecureRegistry() {
        return (getAuthType() != null);
    }

    @Override
    @XmlElement(name="backwards-compatible")
    public boolean isBackwardsCompatible() {
        return isBackwardsCompatible;
    }
}
