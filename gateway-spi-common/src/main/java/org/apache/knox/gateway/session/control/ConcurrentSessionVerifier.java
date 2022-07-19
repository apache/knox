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
package org.apache.knox.gateway.session.control;


import org.apache.knox.gateway.config.GatewayConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentSessionVerifier {
    public static final ConcurrentSessionVerifier INSTANCE = new ConcurrentSessionVerifier();
    private Set<String> privilegedUsers;
    private Set<String> nonPrivilegedUsers;
    private int privilegedUserConcurrentSessionLimit;
    private int nonPrivilegedUserConcurrentSessionLimit;
    private Map<String, Integer> concurrentSessionCounter;
    private Lock verifyLock = new ReentrantLock();
    private Lock decreaseLock = new ReentrantLock();

    private ConcurrentSessionVerifier() {
    }

    public static ConcurrentSessionVerifier getInstance() {
        return INSTANCE;
    }

    public void init(GatewayConfig config) {
        this.privilegedUsers = config.getPrivilegedUsers();
        this.nonPrivilegedUsers = config.getNonPrivilegedUsers();
        this.privilegedUserConcurrentSessionLimit = config.getPrivilegedUsersConcurrentSessionLimit();
        this.nonPrivilegedUserConcurrentSessionLimit = config.getNonPrivilegedUsersConcurrentSessionLimit();
        this.concurrentSessionCounter = new ConcurrentHashMap<>();
    }

    public boolean verifySessionForUser(String username) {
        if (!privilegedUsers.contains(username) && !nonPrivilegedUsers.contains(username)) {
            return true;
        }
        concurrentSessionCounter.putIfAbsent(username, 0);
        verifyLock.lock();
        try {
            if (privilegedUserLimitCheck(username) || nonPrivilegedUserLimitCheck(username)) {
                return false;
            }
            concurrentSessionCounter.compute(username, (key, value) -> value + 1);
        } finally {
            verifyLock.unlock();
        }
        return true;
    }

    private boolean privilegedUserLimitCheck(String username) {
        return privilegedUsers.contains(username) && !(concurrentSessionCounter.get(username) < privilegedUserConcurrentSessionLimit);
    }

    private boolean nonPrivilegedUserLimitCheck(String username) {
        return nonPrivilegedUsers.contains(username) && !(concurrentSessionCounter.get(username) < nonPrivilegedUserConcurrentSessionLimit);
    }

    public void sessionEndedForUser(String username) {
        if (concurrentSessionCounter.containsKey(username)) {
            decreaseLock.lock();
            try {
                int count = concurrentSessionCounter.get(username);
                if (count > 1) {
                    concurrentSessionCounter.put(username, --count);
                }
            } finally {
                decreaseLock.unlock();
            }
        }
    }

    int getUserConcurrentSessionCount(String username) {
        return concurrentSessionCounter.getOrDefault(username, 0);
    }
}
