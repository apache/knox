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

import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.security.token.impl.JWT;

public interface ConcurrentSessionVerifier extends Service {
  /**
   * Verifies whether the given user is permitted to have a[nother] session or not.
   * Similar to the registerToken function, but we need this in WebSSOResource to verify the before the token generation,
   * so in case of an attack we are not wasting resources on token generation and storing.
   *
   * @param username the user who needs verification
   * @return true if the user is allowed to have a[nother] session, false if the user is not allowed to have a[nother] session
   */
  boolean verifySessionForUser(String username);

  /**
   * Verifies whether the given user is permitted to have a[nother] session or not.
   * Also stores the token which the user is using for the session.
   *
   * @param jwtToken token which needs to be stored
   * @param username the user who needs verification
   * @return true if the user is allowed to have a[nother] session, false if the user is not allowed to have a[nother] session
   */
  boolean registerToken(String username, JWT jwtToken);

  void sessionEndedForUser(String username, String token);

}
