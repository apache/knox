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

import java.util.HashSet;
import java.util.Set;
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
    public void testTokenIdDisplayTextNonUuid() {
        // A user-supplied client_id used as the token id is not a UUID; it must still be
        // abbreviated for logging (rather than rendering as null).
        doTestNonUuidTokenDisplay("my-app.prod_1");
    }

    @Test
    public void testTokenIdDisplayTextNonUuidNoDashes() {
        // A 32-char, dashless value is not a valid UUID but is a legitimate token id; abbreviate it.
        final String tokenId = UUID.randomUUID().toString().replace("-", "");
        doTestNonUuidTokenDisplay(tokenId);
    }

    @Test
    public void testTokenIdDisplayTextShortTokenId() {
        // Too short to abbreviate meaningfully -> returned verbatim.
        assertEquals("app", Tokens.getTokenIDDisplayText("app"));
    }

    @Test
    public void testDisplayableTokenIDSet() throws Exception {
        final Set<String> tokenIDs = new HashSet<>();
        for (int i=0 ; i < 5; i++) {
            tokenIDs.add(UUID.randomUUID().toString());
        }

        Set<String> displayableTokenIDs = Tokens.getDisplayableTokenIDsText(tokenIDs);

        for (String displayable : displayableTokenIDs) {
            assertTrue(displayable.length() < 36);
            assertTrue(displayable.contains("..."));
        }
    }

    private void doTestTokenDisplay(final String tokenId) {
        doTestTokenDisplay(tokenId, false);
    }

    private void doTestNonUuidTokenDisplay(final String tokenId) {
        final String displayableTokenId = Tokens.getTokenIDDisplayText(tokenId);
        assertNotNull(displayableTokenId);
        assertTrue(displayableTokenId.length() < tokenId.length());
        assertEquals(tokenId.substring(0, 3) + "..." + tokenId.substring(tokenId.length() - 3),
                     displayableTokenId);
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
