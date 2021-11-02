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
package org.apache.knox.gateway.dto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

public class HomePageProfileTest {

  @Test
  public void testEmptyConfiguration() throws Exception {
    final HomePageProfile profile = new HomePageProfile(Collections.emptySet());
    assertEquals(7, profile.getProfileElements().size());
    profile.getProfileElements().forEach((key, value) -> {
      if (key.startsWith(HomePageProfile.GPI_PREFIX)) {
        assertFalse(Boolean.parseBoolean(value));
      } else {
        assertTrue(value.isEmpty());
      }
    });
  }

  @Test
  public void testGeneralProxyInformationVersion() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_VERSION);
  }

  @Test
  public void testGeneralProxyInformationCert() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_CERT);
  }

  @Test
  public void testGeneralProxyInformationAdminUI() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_ADMIN_UI);
  }

  @Test
  public void testGeneralProxyInformationAdminAPI() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_ADMIN_API);
  }

  @Test
  public void testGeneralProxyInformatioMetadataAPI() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_METADATA_API);
  }

  @Test
  public void testGeneralProxyInformationTokens() throws Exception {
    doTestGeneralProxyInformationElement(HomePageProfile.GPI_TOKENS);
  }

  @Test
  public void testTokenProfileElements() throws Exception {
    final Collection<String> tokenProfileElements = HomePageProfile.getTokenProfileElements();
    assertEquals(3, tokenProfileElements.size());
    assertTrue(tokenProfileElements.containsAll(Arrays.asList(HomePageProfile.GPI_VERSION, HomePageProfile.GPI_CERT, HomePageProfile.GPI_TOKENS)));
  }

  private void doTestGeneralProxyInformationElement(String gpiElement) {
    final HomePageProfile profile = new HomePageProfile(Arrays.asList(gpiElement));
    assertTrue(Boolean.parseBoolean(profile.getProfileElements().get(gpiElement)));
  }
}
