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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * <p>
 * This interface helps processing KnoxSSO cookie invalidation flows in a way
 * such as implementations set the appropriate parameters in the given
 * request/response objects which the {@link SSOCookieFederationFilter} can
 * handle.
 * </p>
 * <p>
 * All implementations must be registered/unregistered in the
 * {@link SessionInvalidators.KNOX_SSO_INVALIDATOR} container, otherwise they
 * will not be notified about authentication errors by
 * {@link SSOCookieFederationFilter}
 * </p>
 * The idea is to implement the observer pattern so that different layers can be
 * decoupled but still be notified about authentication issues.
 * </p>
 *
 * @see <a href="https://issues.apache.org/jira/browse/KNOX-2961">KNOX-2961</a>
 *      for more information on KnoxSSO cookie validation.
 */
public interface SessionInvalidator {

  /**
   * Triggers a notification that an authentication error occurred that is
   * relevant from KnoxSSO cookie invalidation perspective
   */
  void onAuthenticationError(HttpServletRequest request, HttpServletResponse response);

}
