/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.services.knoxidf.delegation;

/**
 * Thrown by {@link DelegationPolicyService#evaluate(PolicyCheckRequest)} when a policy carries a
 * non-empty {@code canActFor.groups} list but its group membership cannot be resolved via LDAP,
 * either because no LDAP service is available (absent or disabled) or because the LDAP lookup call
 * itself failed.
 * <p>
 * In both cases the group rule could not be evaluated - this is a server-side condition, not an
 * authorization decision. Callers must surface it as a server error (directing the operator to
 * ensure LDAP is enabled and reachable) rather than treating it as a policy denial: the exchange
 * must not silently deny with {@code subject_not_allowed}.
 */
public class DelegationGroupLookupUnavailableException extends RuntimeException {

  public DelegationGroupLookupUnavailableException(String subjectName) {
    super("Cannot evaluate canActFor.groups for subject " + subjectName
        + ": the LDAP service is disabled or unavailable");
  }

  public DelegationGroupLookupUnavailableException(String subjectName, Throwable cause) {
    super("Cannot evaluate canActFor.groups for subject " + subjectName
        + ": the LDAP group lookup failed", cause);
  }
}
