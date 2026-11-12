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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DelegatedRequestAuditingTest {

  private Auditor originalAuditor;
  private RecordingAuditor auditor;

  @Before
  public void setUp() {
    originalAuditor = DelegatedRequestAuditing.auditor;
    auditor = new RecordingAuditor();
    DelegatedRequestAuditing.auditor = auditor;
  }

  @After
  public void tearDown() {
    DelegatedRequestAuditing.auditor = originalAuditor;
  }

  @Test
  public void testDisplayTextReplacesControlCharactersAndLineSeparators() {
    assertEquals("a??b?c?d?e?f", DelegatedRequestAuditing.displayText("a\r\nb\tc\u0085d\u2028e\u2029f"));
    assertEquals("svc-a<-svc-b", DelegatedRequestAuditing.displayText("svc-a<-svc-b"));
    assertEquals("", DelegatedRequestAuditing.displayText(null));
  }

  @Test
  public void testDisplayTextTruncatesLongValues() {
    final String max = "x".repeat(DelegatedRequestAuditing.MAX_VALUE_LENGTH);
    assertEquals(max, DelegatedRequestAuditing.displayText(max));
    assertEquals(max + "...", DelegatedRequestAuditing.displayText(max + "tail"));
  }

  @Test
  public void testDisplayTextDoesNotSplitASurrogatePair() {
    final String prefix = "x".repeat(DelegatedRequestAuditing.MAX_VALUE_LENGTH - 1);
    assertEquals(prefix + "...", DelegatedRequestAuditing.displayText(prefix + "\uD83D\uDE00tail"));
  }

  @Test
  public void testFieldQuotesValuesThatWouldReadAsSeveralFields() {
    assertEquals("sub=alice", DelegatedRequestAuditing.field("sub", "alice"));
    assertEquals("sub=", DelegatedRequestAuditing.field("sub", null));
    assertEquals("sub=\"alice result=accepted\"", DelegatedRequestAuditing.field("sub", "alice result=accepted"));
    assertEquals("sub=\"alice\u00A0result=accepted\"",
        DelegatedRequestAuditing.field("sub", "alice\u00A0result=accepted"));
    assertEquals("sub=\"a\\\"b\\\\c\"", DelegatedRequestAuditing.field("sub", "a\"b\\c"));
    assertEquals("sub=a?b", DelegatedRequestAuditing.field("sub", "a\nb"));
  }

  @Test
  public void testDestinationFieldsAreThoseTheAudienceCheckRecorded() {
    final HttpServletRequest mock = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(mock);
    final HttpServletRequest request = K8sAudienceTestSupport.withAttributes(mock);
    assertEquals("", DelegatedRequestAuditing.destinationFields(request));

    request.setAttribute(DelegatedRequestAuditing.DESTINATION_ATTRIBUTE,
        new DelegatedRequestAuditing.Destination("svc.ns.svc.cluster.local", null, "/a b"));
    assertEquals(" dest_service=svc.ns.svc.cluster.local path=\"/a b\"",
        DelegatedRequestAuditing.destinationFields(request));
  }

  @Test
  public void testRejectionOfTokenWithoutActClaimIsNotAudited() {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(request);
    final JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.replay(token);

    DelegatedRequestAuditing.rejected(request, token, "token_expired", null);
    DelegatedRequestAuditing.rejected(request, null, "token_expired", null);

    assertTrue(auditor.entries.isEmpty());
  }

  @Test
  public void testWithoutQueryStripsQueryAndFragment() {
    assertEquals("/a/b", DelegatedRequestAuditing.withoutQuery("/a/b?token=x"));
    assertEquals("/a/b", DelegatedRequestAuditing.withoutQuery("/a/b#frag?x"));
    assertEquals("/a/b", DelegatedRequestAuditing.withoutQuery("/a/b"));
    assertNull(DelegatedRequestAuditing.withoutQuery(null));
  }
}
