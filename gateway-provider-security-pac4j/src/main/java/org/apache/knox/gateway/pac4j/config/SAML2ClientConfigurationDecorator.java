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
package org.apache.knox.gateway.pac4j.config;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.client.Client;
import org.pac4j.saml.client.SAML2Client;

public class SAML2ClientConfigurationDecorator implements ClientConfigurationDecorator {

  private static final String SAML2_CLIENT_CLASS_NAME = SAML2Client.class.getSimpleName();
  private static final String CONFIG_NAME_USE_NAME_QUALIFIER = "useNameQualifier";
  private static final String CONFIG_NAME_USE_FORCE_AUTH = "forceAuth";
  private static final String CONFIG_NAME_USE_PASSIVE = "passive";
  private static final String CONFIG_NAME_NAMEID_POLICY_FORMAT = "nameIdPolicyFormat";

  @Override
  public void decorateClients(List<Client> clients, Map<String, String> properties) {
    for (Client client : clients) {
      if (SAML2_CLIENT_CLASS_NAME.equalsIgnoreCase(client.getName())) {
        final SAML2Client saml2Client = (SAML2Client) client;
        setUseNameQualifierFlag(properties, saml2Client);
        setForceAuthFlag(properties, saml2Client);
        setPassiveFlag(properties, saml2Client);
        setNameIdPolicyFormat(properties, saml2Client);
      }
    }
  }

  private void setUseNameQualifierFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String useNameQualifier = properties.get(CONFIG_NAME_USE_NAME_QUALIFIER);
    if (StringUtils.isNotBlank(useNameQualifier)) {
      saml2Client.getConfiguration().setUseNameQualifier(Boolean.valueOf(useNameQualifier));
    }
  }

  private void setForceAuthFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String forceAuth = properties.get(CONFIG_NAME_USE_FORCE_AUTH);
    if (StringUtils.isNotBlank(forceAuth)) {
      saml2Client.getConfiguration().setForceAuth(Boolean.valueOf(forceAuth));
    }
  }

  private void setPassiveFlag(Map<String, String> properties, final SAML2Client saml2Client) {
    final String passive = properties.get(CONFIG_NAME_USE_PASSIVE);
    if (StringUtils.isNotBlank(passive)) {
      saml2Client.getConfiguration().setPassive(Boolean.valueOf(passive));
    }
  }

  private void setNameIdPolicyFormat(Map<String, String> properties, final SAML2Client saml2Client) {
    final String nameIdPolicyFormat = properties.get(CONFIG_NAME_NAMEID_POLICY_FORMAT);
    if (StringUtils.isNotBlank(nameIdPolicyFormat)) {
      saml2Client.getConfiguration().setNameIdPolicyFormat(nameIdPolicyFormat);
    }
  }
}
