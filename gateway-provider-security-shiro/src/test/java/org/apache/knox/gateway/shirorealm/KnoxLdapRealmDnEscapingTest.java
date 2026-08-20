/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.knox.gateway.shirorealm;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KnoxLdapRealmDnEscapingTest {

  // ---- escapeDnValue: the helper used at BOTH DN-construction sites ----

  @Test
  public void plainValueIsUnchanged() {
    assertEquals("guest", KnoxLdapRealm.escapeDnValue("guest"));
  }

  @Test
  public void dnMetacharactersAreEscaped() {
    // ',' and '=' must be escaped so the value cannot add or alter RDNs
    assertEquals("guest\\,ou\\=admin", KnoxLdapRealm.escapeDnValue("guest,ou=admin"));
  }

  @Test
  public void plusAndQuoteAreEscaped() {
    assertEquals("a\\+b\\\"c", KnoxLdapRealm.escapeDnValue("a+b\"c"));
  }

  @Test
  public void nullIsNullSafe() {
    assertNull(KnoxLdapRealm.escapeDnValue(null));
  }

  // ---- getUserDn: proves the userDnTemplate ({0}) expansion escapes as a DN ----

  @Test
  public void userDnTemplatePlainPrincipalUnchanged() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserDnTemplate("uid={0},ou=people,dc=hadoop,dc=apache,dc=org");
    assertEquals("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org",
        realm.getUserDn("guest"));
  }

  @Test
  public void userDnTemplateInjectionIsEscaped() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserDnTemplate("uid={0},ou=people,dc=hadoop,dc=apache,dc=org");
    // Without escaping this would inject an extra RDN and rewrite the bind DN.
    assertEquals("uid=guest\\,ou\\=admin,ou=people,dc=hadoop,dc=apache,dc=org",
        realm.getUserDn("guest,ou=admin"));
  }
}
