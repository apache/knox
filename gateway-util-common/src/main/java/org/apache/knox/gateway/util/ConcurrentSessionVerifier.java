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


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ConcurrentSessionVerifier {
    private static Set<String> privilegedUsers = new HashSet<>();
    private static Set<String> nonPrivilegedUsers = new HashSet<>();;
    private static int privilegedUserConcurrentSessionLimit = 3;
    private static int nonPrivilegedUserConcurrentSessionLimit = 2;
    private static Map<String, Integer> concurrentSessionCounter = new HashMap<>();

    private ConcurrentSessionVerifier(){}

    public static void init(Set<String> privilegedUsers, Set<String> nonPrivilegedUsers, int privilegedUserConcurrentSessionLimit, int nonPrivilegedUserConcurrentSessionLimit){
        ConcurrentSessionVerifier.privilegedUsers = privilegedUsers;
        ConcurrentSessionVerifier.nonPrivilegedUsers = nonPrivilegedUsers;
        ConcurrentSessionVerifier.privilegedUserConcurrentSessionLimit = privilegedUserConcurrentSessionLimit;
        ConcurrentSessionVerifier.nonPrivilegedUserConcurrentSessionLimit = nonPrivilegedUserConcurrentSessionLimit;
        concurrentSessionCounter = new HashMap<>();
    }

    public  static boolean verifySessionForUser(String username){
        if(!privilegedUsers.contains(username) && !nonPrivilegedUsers.contains(username)) {
            return true;
        }
        if(!concurrentSessionCounter.containsKey(username)){
            concurrentSessionCounter.put(username, 0);
        }

        if((privilegedUsers.contains(username) && !(concurrentSessionCounter.get(username) < privilegedUserConcurrentSessionLimit)) || (nonPrivilegedUsers.contains(username) && !(concurrentSessionCounter.get(username) < nonPrivilegedUserConcurrentSessionLimit))){
            return false;
        }

        incrementConcurrentSessionCount(username);
        return true;
    }

    private static void incrementConcurrentSessionCount(String username){
        int count = concurrentSessionCounter.get(username);
        count++;
        concurrentSessionCounter.put(username, count);
    }

    public static void sessionEndedForUser(String username){
        if(concurrentSessionCounter.containsKey(username)){
            int count = concurrentSessionCounter.get(username);
            count--;
            if(count < 0) {
                concurrentSessionCounter.put(username, 0);
            }else {
                concurrentSessionCounter.put(username, count);
            }
        }
    }
}
