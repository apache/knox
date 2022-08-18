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


import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.impl.JWT;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class InMemoryConcurrentSessionVerifier implements ConcurrentSessionVerifier {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);

  private Set<String> privilegedUsers;
  private Set<String> nonPrivilegedUsers;
  private int privilegedUserConcurrentSessionLimit;
  private int nonPrivilegedUserConcurrentSessionLimit;
  private Map<String, Set<SessionJWT>> concurrentSessionCounter;
  private final Lock sessionCountModifyLock = new ReentrantLock();

  @Override
  public boolean verifySessionForUser(String username, JWT jwtToken) {
    if (StringUtils.isBlank(username)) {
      LOG.errorVerifyingUserBlankUsername();
      return false;
    }
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
      concurrentSessionCounter.compute(username, (key, sessionTokenSet) -> addTokenForUser(sessionTokenSet, jwtToken));
    } finally {
      sessionCountModifyLock.unlock();
    }
    return true;
  }

  int countValidTokensForUser(String username) {
    return (int) concurrentSessionCounter
            .getOrDefault(username, Collections.emptySet())
            .stream()
            .filter(each -> !each.hasExpired())
            .count();
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

  @Override
  public void sessionEndedForUser(String username, String token) {
    if (StringUtils.isNotBlank(token) && StringUtils.isNotBlank(username)) {
      sessionCountModifyLock.lock();
      try {
        concurrentSessionCounter.computeIfPresent(username, (key, sessionTokenSet) -> removeTokenFromUser(sessionTokenSet, token));
      } finally {
        sessionCountModifyLock.unlock();
      }
    }
  }

  private Set<SessionJWT> removeTokenFromUser(Set<SessionJWT> sessionTokenSet, String token) {
    sessionTokenSet.removeIf(sessionToken -> sessionToken.getToken().equals(token));
    if (sessionTokenSet.isEmpty()) {
      return null;
    }
    return sessionTokenSet;
  }

  private Set<SessionJWT> addTokenForUser(Set<SessionJWT> sessionTokenSet, JWT jwtToken) {
    sessionTokenSet.add(new SessionJWT(jwtToken));
    return sessionTokenSet;
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

  public static class SessionJWT {
    private final Date expiry;
    private final String token;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SessionJWT that = (SessionJWT) o;
      return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
      return Objects.hash(token);
    }

    public SessionJWT(JWT token) {
      this.expiry = token.getExpiresDate();
      this.token = token.toString();
    }

    public String getToken() {
      return token;
    }

    public boolean hasExpired() {
      return expiry != null && expiry.before(new Date());
    }
  }
}
