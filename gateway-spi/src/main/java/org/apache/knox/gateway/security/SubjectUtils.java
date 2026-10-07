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
package org.apache.knox.gateway.security;

import de.thetaphi.forbiddenapis.SuppressForbidden;

import javax.security.auth.Subject;

import java.security.AccessController;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * General utility methods for interrogating the standard java Subject
 */
public class SubjectUtils {

  /*
   * There is no option in JDK 17 other then suppressing.
   * For JDK 18+ use Subject.current() instead.
   */
  @SuppressForbidden
  public static Subject getCurrentSubject() {
    return Subject.getSubject( AccessController.getContext() );
  }

  public static String getPrimaryPrincipalName(Subject subject) {
    String name = null;

    Set<PrimaryPrincipal> primaryPrincipals = subject.getPrincipals(PrimaryPrincipal.class);
    if (!primaryPrincipals.isEmpty()) {
      return ((PrimaryPrincipal)primaryPrincipals.toArray()[0]).getName();
    }

    // LJM TODO: this implementation assumes the first one found.
    // We should configure through context param based on knowledge
    // of the authentication provider in use
    Optional<Principal> principal = subject.getPrincipals().stream().findFirst();
    if (principal.isPresent()) {
      name = principal.get().getName();
    }

    return name;
  }

  public static boolean isImpersonating(Subject subject) {
    return (!subject.getPrincipals(ImpersonatedPrincipal.class).isEmpty());
  }

  public static String getImpersonatedPrincipalName(Subject subject) {
    String name = null;

    Set<ImpersonatedPrincipal> impPrincipals = subject.getPrincipals(ImpersonatedPrincipal.class);
    if (!impPrincipals.isEmpty()) {
      return ((Principal)impPrincipals.toArray()[0]).getName();
    }

    return name;
  }

  public static String getEffectivePrincipalName(Subject subject) {
    String name = getImpersonatedPrincipalName(subject);
    if (name == null) {
      name = getPrimaryPrincipalName(subject);
    }

    return name;
  }

  public static String getCurrentEffectivePrincipalName() {
    final Subject subject = getCurrentSubject();
    return subject == null ? null : getEffectivePrincipalName(subject);
  }

  public static Set<GroupPrincipal> getCurrentGroupPrincipals() {
    final Subject subject = getCurrentSubject();
    return subject == null ? Collections.emptySet() : getGroupPrincipals(subject);
  }

  public static Set<GroupPrincipal> getGroupPrincipals(Subject subject) {
    return subject.getPrincipals(GroupPrincipal.class);
  }

  public static Set<TokenIdPrincipal> getTokenIdPrincipals(Subject subject) {
    return subject.getPrincipals(TokenIdPrincipal.class);
  }

  /**
   * Get the caller's auth token credentials from the subject's private credentials.
   *
   * <p>The token is carried as a private credential rather than a principal so that it can
   * never be mistaken for an identity by {@link #getPrimaryPrincipalName(Subject)}.</p>
   *
   * @param subject the subject to interrogate
   * @return the auth token credentials, empty when the subject is null or carries none
   */
  public static Set<AuthTokenCredential> getAuthTokenCredentials(Subject subject) {
    return subject == null ? Collections.emptySet() : subject.getPrivateCredentials(AuthTokenCredential.class);
  }

  /**
   * Get the caller's serialized JWT from the subject, if one was captured at authentication time.
   *
   * @param subject the subject to interrogate
   * @return the serialized token, or null when the subject is null or carries no auth token
   */
  public static String getAuthToken(Subject subject) {
    final Set<AuthTokenCredential> credentials = getAuthTokenCredentials(subject);
    return credentials.isEmpty() ? null : credentials.iterator().next().getToken();
  }

  public static Set<ActorChainPrincipal> getActorChainPrincipal(Subject currentSubject, Subject subject) {
    return currentSubject.getPrincipals(ActorChainPrincipal.class);
  }

  public static ActorChainPrincipal getActorChainPrincipal(Subject subject) {
    if (subject == null) {
      return null;
    }
    final Set<ActorChainPrincipal> principals = subject.getPrincipals(ActorChainPrincipal.class);
    return principals.isEmpty() ? null : principals.iterator().next();
  }

  public static String renderActorChain(List<Map<String, Object>> actorChain) {
    if (actorChain == null || actorChain.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder();
    for (Map<String, Object> actor : actorChain) {
      if (sb.length() > 0) {
        sb.append("<-");
      }
      final Object iss = actor.get("iss");
      if (iss != null) {
        sb.append(iss).append('/');
      }
      final Object sub = actor.get("sub");
      sb.append(sub == null ? "" : sub.toString());
    }
    return sb.toString();
  }

  public static String renderActorChain(ActorChainPrincipal principal) {
    return principal == null ? "" : renderActorChain(principal.getActorChain());
  }

  /**
   * Get the TokenExchangePrincipal from the subject if present.
   *
   * <p>This is used to detect when an RFC 8693 token exchange has occurred
   * and to extract the subject and actor identities.</p>
   *
   * @param subject the subject to check
   * @return the TokenExchangePrincipal if present, null otherwise
   */
  public static TokenExchangePrincipal getTokenExchangePrincipal(Subject subject) {
    if (subject == null) {
      return null;
    }
    Set<TokenExchangePrincipal> principals = subject.getPrincipals(TokenExchangePrincipal.class);
    return principals.isEmpty() ? null : principals.iterator().next();
  }
}
