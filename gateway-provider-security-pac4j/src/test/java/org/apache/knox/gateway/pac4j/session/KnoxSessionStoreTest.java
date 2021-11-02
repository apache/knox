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
package org.apache.knox.gateway.pac4j.session;

import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.saml.profile.SAML2Profile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT;
import static org.apache.knox.gateway.pac4j.session.KnoxSessionStore.PAC4J_PASSWORD;

public class KnoxSessionStoreTest {
  private static final String CLUSTER_NAME = "knox";

  /**
   * Test exclusion of groups, roles and permissions
   * from pac4j profile object that is saved as a cookie.
   * @throws AliasServiceException
   */
  @Test
  public void filterConfigParamsTest()
      throws AliasServiceException {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD, true))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
    EasyMock.replay(aliasService);

    final DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(aliasService);

    final Map<String, String> sessionStoreConfigs = new HashMap();

    final Capture<org.pac4j.core.context.Cookie> captureCookieValue = EasyMock.newCapture();
    final WebContext mockContext = EasyMock.createNiceMock(WebContext.class);
    EasyMock.expect(mockContext.getFullRequestURL()).andReturn("https://local.com/gateway/knoxsso/").anyTimes();
    mockContext.addResponseCookie(EasyMock.capture(captureCookieValue));

    EasyMock.replay(mockContext);


    final SAML2Profile samlProfile = new SAML2Profile();
    Set<String> groups = new HashSet<>(Arrays.asList("admin_2", "admin_1", "admin"));
    Set<String> roles = new HashSet<>(Arrays.asList("roles_2", "roles_1", "roles"));
    Set<String> permissions = new HashSet<>(Arrays.asList("permissions_2", "permissions_1", "permissions"));
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("groups", groups);
    attributes.put("permissions", permissions);
    attributes.put("roles", roles);
    samlProfile.addAttributes(attributes);

    /*
     * Test the default behavior where groups, roles and permissions are
     * excluded from the cookie.
     */

    /* Make sure groups are present */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));


    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_GROUPS, PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT);
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_ROLES, PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT);
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT);

    final Map<String, CommonProfile> profile = new HashMap<>();
    profile.put("SAML2Client", samlProfile);

    final KnoxSessionStore sessionStore = new KnoxSessionStore(cryptoService, CLUSTER_NAME, null, sessionStoreConfigs);

    sessionStore.set(mockContext, Pac4jConstants.USER_PROFILES, profile);

    /* Make sure groups are removed */
    Assert.assertNull(samlProfile.getAttribute("groups"));
    Assert.assertNull(samlProfile.getAttribute("roles"));
    Assert.assertNull(samlProfile.getAttribute("permissions"));


    /*
     * Test the override behavior where groups, roles and permissions are
     * not-excluded from the cookie.
     */
    attributes.put("groups", groups);
    attributes.put("permissions", permissions);
    attributes.put("roles", roles);
    samlProfile.addAttributes(attributes);

    /* Make sure groups are present */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));


    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_GROUPS, "false");
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_ROLES, "false");
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, "false");

    profile.put("SAML2Client", samlProfile);

    sessionStore.set(mockContext, Pac4jConstants.USER_PROFILES, profile);

    /* Make sure groups are removed */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));
  }
}
