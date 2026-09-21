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
package org.apache.knox.gateway.security;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class SubjectUtilsTest {

  private static Map<String, Object> actor(String iss, String sub) {
    final Map<String, Object> actor = new LinkedHashMap<>();
    if (iss != null) {
      actor.put("iss", iss);
    }
    if (sub != null) {
      actor.put("sub", sub);
    }
    return actor;
  }

  @Test
  public void testRenderActorChainWithIssuerSubjectPairs() {
    final List<Map<String, Object>> chain = Arrays.asList(
        actor("idp1", "svc-c"), actor("idp1", "svc-b"), actor("idp2", "svc-a"));
    assertEquals("idp1/svc-c<-idp1/svc-b<-idp2/svc-a", SubjectUtils.renderActorChain(chain));
  }

  @Test
  public void testRenderActorChainFallsBackToSubjectWhenNoIssuer() {
    final List<Map<String, Object>> chain = Arrays.asList(actor(null, "actor-1"), actor(null, "actor-2"));
    assertEquals("actor-1<-actor-2", SubjectUtils.renderActorChain(chain));
  }

  @Test
  public void testRenderActorChainMixesPresentAndAbsentIssuers() {
    final List<Map<String, Object>> chain = Arrays.asList(actor("idp1", "svc-c"), actor(null, "svc-a"));
    assertEquals("idp1/svc-c<-svc-a", SubjectUtils.renderActorChain(chain));
  }

  @Test
  public void testRenderActorChainEmptyAndNull() {
    assertEquals("", SubjectUtils.renderActorChain((List<Map<String, Object>>) null));
    assertEquals("", SubjectUtils.renderActorChain(Collections.emptyList()));
    assertEquals("", SubjectUtils.renderActorChain((ActorChainPrincipal) null));
  }

  @Test
  public void testRenderActorChainFromPrincipal() {
    final List<Map<String, Object>> chain = Arrays.asList(actor("idp1", "svc-c"), actor("idp2", "svc-a"));
    assertEquals("idp1/svc-c<-idp2/svc-a",
        SubjectUtils.renderActorChain(new ActorChainPrincipalImpl(chain)));
  }
}
