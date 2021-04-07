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
package org.apache.knox.gateway.services.token.impl.state;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.token.state.TokenStateJournal;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MultiFileTokenStateJournalTest extends AbstractFileTokenStateJournalTest {

    @Override
    TokenStateJournal createTokenStateJournal(GatewayConfig config) throws IOException {
        return new MultiFileTokenStateJournal(config);
    }

    @Test
    public void testGetDisplayableJournalFilepathWithoutID() throws Exception {
        final String dirPath = "/tmp/test/tokens/journal/";
        final String tokenId = UUID.randomUUID().toString();
        final String entryFilePath = dirPath + tokenId + MultiFileTokenStateJournal.ENTRY_FILE_EXT;
        MultiFileTokenStateJournal journal = (MultiFileTokenStateJournal) createTokenStateJournal(getGatewayConfig());
        Method m = MultiFileTokenStateJournal.class.getDeclaredMethod("getDisplayableJournalFilepath", String.class);
        assertNotNull(m);
        m.setAccessible(true);
        String displayablePath = (String) m.invoke(journal, entryFilePath);
        assertNotNull(displayablePath);
        assertTrue(displayablePath.length() < entryFilePath.length());
        assertFalse(displayablePath.contains(tokenId));
    }

    @Test
    public void testGetDisplayableJournalFilepathWithID() throws Exception {
        final String dirPath = "/tmp/test/tokens/journal/";
        final String tokenId = UUID.randomUUID().toString();
        final String entryFilePath = dirPath + tokenId + MultiFileTokenStateJournal.ENTRY_FILE_EXT;
        MultiFileTokenStateJournal journal = (MultiFileTokenStateJournal) createTokenStateJournal(getGatewayConfig());
        Method m = MultiFileTokenStateJournal.class.getDeclaredMethod("getDisplayableJournalFilepath",
                                                                      String.class,
                                                                      String.class);
        assertNotNull(m);
        m.setAccessible(true);
        String displayablePath = (String) m.invoke(journal, tokenId, entryFilePath);
        assertNotNull(displayablePath);
        assertTrue(displayablePath.length() < entryFilePath.length());
        assertFalse(displayablePath.contains(tokenId));
    }

}
