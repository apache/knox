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
package org.apache.knox.gateway.service.knoxidf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the consent metadata key derivation (finding 2.11). Consent is stored in
 * {@code KNOX_TOKEN_METADATA.md_name VARCHAR(32)}; the key must therefore stay within 32 chars for
 * every subject, be deterministic (so a later read finds an earlier write), and separate distinct
 * subjects.
 */
public class ConsentMetadataKeyTest {

  /** The backing column is VARCHAR(32). */
  private static final int MD_NAME_MAX = 32;

  @Test
  public void testKeyFitsColumnForRealisticSubjects() {
    final String[] subjects = {
        "alice",
        "administrator@corp.example.com",
        // a federated UUID subject - the case that overflowed the old "consentAccepted_" + subject
        "b9f8e7d6-c5a4-4321-9876-0123456789abcdef-very-long-external-subject-identifier",
        "",
    };
    for (final String subject : subjects) {
      final String key = AuthorizeResource.consentMetadataKey(subject);
      assertTrue("Key '" + key + "' (" + key.length() + " chars) must fit VARCHAR(" + MD_NAME_MAX + ")",
          key.length() <= MD_NAME_MAX);
      assertTrue("Key should carry the consent_ prefix", key.startsWith("consent_"));
    }
  }

  @Test
  public void testKeyIsDeterministic() {
    final String subject = "b9f8e7d6-c5a4-4321-9876-0123456789ab";
    assertEquals("Same subject must always derive the same key (read must match write).",
        AuthorizeResource.consentMetadataKey(subject), AuthorizeResource.consentMetadataKey(subject));
  }

  @Test
  public void testDistinctSubjectsDeriveDistinctKeys() {
    assertNotEquals(AuthorizeResource.consentMetadataKey("alice"),
        AuthorizeResource.consentMetadataKey("bob"));
  }
}
