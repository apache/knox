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
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;

import java.text.ParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrentSessionVerifier implements Service {
  private Set<String> privilegedUsers;
  private Set<String> nonPrivilegedUsers;
  private int privilegedUserConcurrentSessionLimit;
  private int nonPrivilegedUserConcurrentSessionLimit;
  private Map<String, Set<String>> concurrentSessionCounter;
  private final Lock sessionCountModifyLock = new ReentrantLock();

  public boolean verifySessionForUser(String username, String token) {
    if (!privilegedUsers.contains(username) && !nonPrivilegedUsers.contains(username)) {
      return true;
    }

    sessionCountModifyLock.lock();
    try {
      int validTokenNumber = countValidTokensForUser(username);
      if (privilegedUserCheckLimitReached(username, validTokenNumber) || nonPrivilegedUserCheckLimitReached(username, validTokenNumber)) {
        return false;
      }
      concurrentSessionCounter.putIfAbsent(username, new HashSet<>());
      concurrentSessionCounter.compute(username, (key, tokenSet) -> addTokenForUser(tokenSet, token));
    } finally {
      sessionCountModifyLock.unlock();
    }
    return true;
  }

  private int countValidTokensForUser(String username) {
    int result = 0;
    Set<String> tokens = concurrentSessionCounter.getOrDefault(username, null);
    if (tokens != null && !tokens.isEmpty()) {
      for (String token : tokens) {
        try {
          JWT jwtToken = new JWTToken(token);
          Date expire = jwtToken.getExpiresDate();
          if (expire == null || expire.after(new Date())) {
            result++;
          }
        } catch (ParseException ignore) {
        }
      }
    }
    return result;
  }

  private boolean privilegedUserCheckLimitReached(String username, int validTokenNumber) {
    if (privilegedUserConcurrentSessionLimit < 0) {
      return false;
    }
    return privilegedUsers.contains(username) && (validTokenNumber >= privilegedUserConcurrentSessionLimit);
  }

  private boolean nonPrivilegedUserCheckLimitReached(String username, int validTokenNumber) {
    if (nonPrivilegedUserConcurrentSessionLimit < 0) {
      return false;
    }
    return nonPrivilegedUsers.contains(username) && (validTokenNumber >= nonPrivilegedUserConcurrentSessionLimit);
  }

  public void sessionEndedForUser(String username, String token) {
    if (!token.isEmpty()) {
      sessionCountModifyLock.lock();
      try {
        concurrentSessionCounter.computeIfPresent(username, (key, tokenSet) -> removeTokenFromUser(tokenSet, token));
      } finally {
        sessionCountModifyLock.unlock();
      }
    }
  }

  private Set<String> removeTokenFromUser(Set<String> tokenSet, String newToken) {
    tokenSet.remove(newToken);
    if (tokenSet.isEmpty()) {
      return null;
    }
    return tokenSet;
  }

  private Set<String> addTokenForUser(Set<String> tokens, String newToken) {
    tokens.add(newToken);
    return tokens;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    this.privilegedUsers = config.getPrivilegedUsers();
    this.nonPrivilegedUsers = config.getNonPrivilegedUsers();
    this.privilegedUserConcurrentSessionLimit = config.getPrivilegedUsersConcurrentSessionLimit();
    this.nonPrivilegedUserConcurrentSessionLimit = config.getNonPrivilegedUsersConcurrentSessionLimit();
    this.concurrentSessionCounter = new ConcurrentHashMap<>();
  }

  @Override
  public void start() throws ServiceLifecycleException {

  }

  @Override
  public void stop() throws ServiceLifecycleException {

  }

  Integer getUserConcurrentSessionCount(String username) {
    int result = countValidTokensForUser(username);
    return (result == 0) ? null : result;
  }
}
