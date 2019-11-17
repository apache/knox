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

import javax.security.auth.Subject;
import java.security.AccessController;
import java.security.Principal;
import java.util.Optional;
import java.util.Set;

/**
 * General utility methods for interrogating the standard java Subject
 */
public class SubjectUtils {
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
    String name = null;
    Subject subject = getCurrentSubject();
    if( subject != null ) {
      name = getImpersonatedPrincipalName( subject );
      if (name == null) {
        name = getPrimaryPrincipalName(subject);
      }
    }
    return name;
  }

}
