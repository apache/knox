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
package org.apache.knox.gateway.session;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A singleton container for {@link SessionInvalidator} instances that must be used
 * within the JVM when dealing with KnoxSSO cookie invalidation signals.
 *
 * All {@link SessionInvalidator} implementations should register/unregister themselves
 * here upon their own initialization/destroy phases.
 */
public enum SessionInvalidators {

  /**
   * The only session invalidatiors container instance.
   */
  KNOX_SSO_INVALIDATOR;

  private final Set<SessionInvalidator> sessionInvalidators = Collections.synchronizedSet(new HashSet<>());

  public void registerSessionInvalidator(SessionInvalidator sessionInvalidator) {
    sessionInvalidators.add(sessionInvalidator);
  }

  public void unregisterSessionInvalidator(SessionInvalidator sessionInvalidator) {
    sessionInvalidators.remove(sessionInvalidator);
  }

  public Set<SessionInvalidator> getSessionInvalidators() {
    return sessionInvalidators;
  }
}
