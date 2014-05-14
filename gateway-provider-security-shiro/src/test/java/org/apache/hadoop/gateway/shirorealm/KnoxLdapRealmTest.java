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

package org.apache.hadoop.gateway.shirorealm;

import org.junit.Test;
import static org.junit.Assert.*;

public class KnoxLdapRealmTest {
  
  @Test
  public void setGetSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=hadoop,dc=apache,dc=org");
    assertEquals(realm.getSearchBase(), "dc=hadoop,dc=apache,dc=org");
  }
  
  @Test
  public void setGetGroupObjectClass() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setGroupObjectClass("groupOfMembers");
    assertEquals(realm.getGroupObjectClass(), "groupOfMembers");
  }  
  
  @Test
  public void setGetUniqueMemberAttribute() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setMemberAttribute("member");
    assertEquals(realm.getMemberAttribute(), "member");
  }
  
  @Test
  public void setGetUserSearchAttributeName() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserSearchAttributeName("uid");
    assertEquals(realm.getUserSearchAttributeName(), "uid");
  }
  
  @Test
  public void setGetUserObjectClass() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserObjectClass("inetuser");
    assertEquals(realm.getUserObjectClass(), "inetuser");
  }
  
  
}
