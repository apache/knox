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
package org.apache.knox.gateway.services.security.token;

/**
 * Thrown when a token cannot be added because its identifier ({@code KNOX_TOKENS.token_id})
 * already exists. This surfaces a unique/primary-key constraint violation from the persistent
 * token store so callers (e.g. the client-credentials endpoint honoring a user-supplied
 * {@code clientId}) can translate it into an HTTP 409 Conflict rather than a generic 500.
 *
 * <p>It extends {@link TokenStateServiceException} (itself unchecked), so it requires no change to
 * the {@link TokenStateService} interface signatures and is transparent to callers that do not
 * distinguish it.</p>
 */
@SuppressWarnings("serial")
public class TokenAlreadyExistsException extends TokenStateServiceException {

  public TokenAlreadyExistsException(String message) {
    super(message);
  }

  public TokenAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }

}
