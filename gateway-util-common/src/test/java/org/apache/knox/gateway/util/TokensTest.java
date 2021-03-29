/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements. See the NOTICE file distributed with this
 *  * work for additional information regarding copyright ownership. The ASF
 *  * licenses this file to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */
package org.apache.knox.gateway.util;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TokensTest {

    @Test
    public void testTokenIdDisplayText() {
        doTestTokenDisplay(UUID.randomUUID().toString());
    }

    @Test
    public void testTokenIdDisplayTextEmptyUUID() {
        doTestTokenDisplay("", true);
    }

    @Test
    public void testTokenIdDisplayTextNullUUID() {
        doTestTokenDisplay(null, true);
    }

    @Test
    public void testTokenIdDisplayTextShortUUID() {
        final String tokenId = UUID.randomUUID().toString();
        doTestTokenDisplay(tokenId.substring(1), true);
    }

    @Test
    public void testTokenIdDisplayTextInvalidUUID() {
        final String tokenId = UUID.randomUUID().toString();
        // Strip the '-' from the token ID
        char[] invalid = new char[tokenId.length() - 4];
        int count = 0;
        for (int i = 0 ; i < tokenId.length(); i++) {
            char c = tokenId.charAt(i);
            if (c != '-') {
                invalid[count++] = tokenId.charAt(i);
            }
        }
        // Invoke the test with the invalid ID
        doTestTokenDisplay(new String(invalid), true);
    }

    private void doTestTokenDisplay(final String tokenId) {
        doTestTokenDisplay(tokenId, false);
    }

    private void doTestTokenDisplay(final String tokenId, final Boolean invalidID) {
        String displayableTokenId = Tokens.getTokenIDDisplayText(tokenId);
        if (invalidID) {
            assertNull("Expected null because the tokenId is invalid.", displayableTokenId);
        } else {
            assertNotNull(displayableTokenId);
            assertTrue(displayableTokenId.length() < tokenId.length());
            assertEquals("Unexpected result for displayable token UUID.",
                         tokenId.substring(0, tokenId.indexOf('-')) + "..." + tokenId.substring(tokenId.lastIndexOf('-') + 1),
                         displayableTokenId);
        }
    }
}
