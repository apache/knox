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
package org.apache.knox.gateway.util;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConcurrentSessionVerifierTest {

    @Test
    public void userIsInNeitherOfTheGroups(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
        for(int i = 0; i < 4; i++){
            assertTrue(ConcurrentSessionVerifier.verifySessionForUser("sam"));
        }
    }

    @Test
    public void userIsInBothOfTheGroups(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin","tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));

        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin","tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 4);
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
    }

    @Test
    public void userIsPrivileged(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        ConcurrentSessionVerifier.sessionEndedForUser("admin");
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
    }

    @Test
    public void userIsNotPrivileged(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        ConcurrentSessionVerifier.sessionEndedForUser("tom");
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
    }

    @Test
    public void privilegedLimitIsZero(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 0, 2);
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
    }

    @Test
    public void nonPrivilegedLimitIsZero(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 0);
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
    }

    @Test
    public void sessionsDoNotGoToNegative(){
        ConcurrentSessionVerifier.init(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 2, 2);
        ConcurrentSessionVerifier.sessionEndedForUser("admin");
        ConcurrentSessionVerifier.sessionEndedForUser("admin");
        ConcurrentSessionVerifier.sessionEndedForUser("tom");
        ConcurrentSessionVerifier.sessionEndedForUser("tom");
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("tom"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertTrue(ConcurrentSessionVerifier.verifySessionForUser("admin"));
        assertFalse(ConcurrentSessionVerifier.verifySessionForUser("admin"));
    }
}
