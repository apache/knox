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
package org.apache.knox.gateway.util.knoxidf;

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.AUTH_CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_CREDENTIALS;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.ServletRequest;

import org.easymock.EasyMock;
import org.junit.Test;

public class AllowedGrantTypesTest {

  private static final String[] SUPPORTED = {AUTH_CODE, REFRESH_TOKEN, CLIENT_CREDENTIALS, TOKEN_EXCHANGE_GRANT_TYPE};

  @Test
  public void testUnsetOrBlankIsUnrestricted() {
    for (final String configured : new String[]{null, "", "   ", " , ,"}) {
      final AllowedGrantTypes allowed = AllowedGrantTypes.parse(configured);
      assertSame("'" + configured + "' must mean unrestricted.", AllowedGrantTypes.ALL, allowed);
      assertTrue("A missing grant_type is accepted when unrestricted.", allowed.isAllowed(null));
      assertTrue(allowed.isAllowed(""));
      assertTrue(allowed.isAllowed("password"));
      assertEquals(List.of(SUPPORTED), List.copyOf(allowed.grantTypesSupported()));
    }
  }

  @Test
  public void testReadsTheRequestAttribute() {
    final AllowedGrantTypes configured = AllowedGrantTypes.parse(TOKEN_EXCHANGE_GRANT_TYPE);
    final AllowedGrantTypes allowed = AllowedGrantTypes.from(requestWithAttribute(configured));
    assertSame(configured, allowed);
    assertTrue(allowed.isAllowed(TOKEN_EXCHANGE_GRANT_TYPE));
    assertFalse(allowed.isAllowed(AUTH_CODE));
  }

  @Test
  public void testNoUsableAttributeIsUnrestricted() {
    assertSame("No request.", AllowedGrantTypes.ALL, AllowedGrantTypes.from(null));
    assertSame("No attribute.", AllowedGrantTypes.ALL, AllowedGrantTypes.from(requestWithAttribute(null)));
    assertSame("A wrong-typed attribute is ignored.", AllowedGrantTypes.ALL,
        AllowedGrantTypes.from(requestWithAttribute(TOKEN_EXCHANGE_GRANT_TYPE)));
  }

  @Test
  public void testIsRestricted() {
    assertFalse(AllowedGrantTypes.ALL.isRestricted());
    assertFalse(AllowedGrantTypes.parse(" ").isRestricted());
    assertTrue(AllowedGrantTypes.parse(TOKEN_EXCHANGE_GRANT_TYPE).isRestricted());
    assertTrue("A list naming no supported grant is still restrictive.", AllowedGrantTypes.parse("password").isRestricted());
  }

  @Test
  public void testAuditNameNeverEchoesCallerInput() {
    for (final String grantType : SUPPORTED) {
      assertEquals(grantType, AllowedGrantTypes.auditName(grantType));
    }
    assertEquals("none", AllowedGrantTypes.auditName(null));
    assertEquals("none", AllowedGrantTypes.auditName(" "));
    assertEquals("other", AllowedGrantTypes.auditName("password"));
    assertEquals("other", AllowedGrantTypes.auditName("x\nevent=forged"));
  }

  @Test
  public void testListRestrictsToTrimmedEntries() {
    final AllowedGrantTypes allowed = AllowedGrantTypes.parse(" " + TOKEN_EXCHANGE_GRANT_TYPE + " ,, " + REFRESH_TOKEN + " ");
    assertTrue(allowed.isAllowed(TOKEN_EXCHANGE_GRANT_TYPE));
    assertTrue(allowed.isAllowed(REFRESH_TOKEN));
    assertFalse(allowed.isAllowed(AUTH_CODE));
    assertFalse(allowed.isAllowed(CLIENT_CREDENTIALS));
    assertFalse("Matching is exact.", allowed.isAllowed(REFRESH_TOKEN.toUpperCase(Locale.ROOT)));
    assertFalse("A missing grant_type is rejected once the list is set.", allowed.isAllowed(null));
    assertFalse(allowed.isAllowed(""));
    assertFalse(allowed.isAllowed(" "));
  }

  @Test
  public void testAdvertisesTheConfiguredList() {
    final AllowedGrantTypes allowed = AllowedGrantTypes.parse(TOKEN_EXCHANGE_GRANT_TYPE + "," + AUTH_CODE);
    assertEquals(List.of(TOKEN_EXCHANGE_GRANT_TYPE, AUTH_CODE), List.copyOf(allowed.grantTypesSupported()));
  }

  @Test
  public void testOnlyUnknownValuesRejectEverything() {
    final AllowedGrantTypes allowed = AllowedGrantTypes.parse("password");
    for (final String grantType : SUPPORTED) {
      assertFalse(grantType + " must be rejected.", allowed.isAllowed(grantType));
    }
    assertFalse("A list naming no supported grant must not fall back to unrestricted.", allowed.isAllowed(null));
  }

  private static ServletRequest requestWithAttribute(Object attribute) {
    final ServletRequest request = EasyMock.createNiceMock(ServletRequest.class);
    EasyMock.expect(request.getAttribute(KnoxIDFConstants.ALLOWED_GRANT_TYPES_ATTRIBUTE)).andReturn(attribute).anyTimes();
    EasyMock.replay(request);
    return request;
  }
}
