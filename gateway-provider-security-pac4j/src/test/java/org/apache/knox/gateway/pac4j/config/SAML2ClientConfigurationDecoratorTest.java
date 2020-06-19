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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;

public class SAML2ClientConfigurationDecoratorTest {

  @Test
  public void testSaml2ClientConfigurationDecoration() throws Exception {
    final SAML2Configuration saml2Configuration = new SAML2Configuration();
    final SAML2Client client = new SAML2Client(saml2Configuration);
    final Map<String, String> properties = new HashMap<>();
    properties.put("useNameQualifier", "true");
    properties.put("forceAuth", "true");
    properties.put("passive", "true");
    properties.put("nameIdPolicyFormat", "testPolicyFormat");

    final SAML2ClientConfigurationDecorator saml2ConfigurationDecorator = new SAML2ClientConfigurationDecorator();
    saml2ConfigurationDecorator.decorateClients(Collections.singletonList(client), properties);
    assertTrue(saml2Configuration.isUseNameQualifier());
    assertTrue(saml2Configuration.isForceAuth());
    assertTrue(saml2Configuration.isPassive());
    assertEquals("testPolicyFormat", saml2Configuration.getNameIdPolicyFormat());
  }

}
