/**
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
package org.apache.hadoop.gateway.pac4j.filter;

import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.profile.ProfileHelper;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.HashMap;

public abstract class Pac4jSessionFilter implements Filter {

  protected Logger logger = LoggerFactory.getLogger(getClass());

  private static final String PAC4J_SESSION_COOKIE_PREFIX = "pac4j.session.";

  private void addCookie(final HttpServletResponse response, final String suffix, final String value) {
    final Cookie cookie = new Cookie(PAC4J_SESSION_COOKIE_PREFIX + suffix, value);
    response.addCookie(cookie);
  }

  protected void saveSession(final HttpServletRequest request, final HttpServletResponse response) {
    final HttpSession session = request.getSession();
    final String requestedUrl = (String) session.getAttribute(Pac4jConstants.REQUESTED_URL);
    logger.info("Save requestedUrl: {}", requestedUrl);
    if (requestedUrl != null) {
      addCookie(response, Pac4jConstants.REQUESTED_URL, requestedUrl);
    }
    final J2EContext context = new J2EContext(request, response);
    final ProfileManager manager = new ProfileManager(context);
    final UserProfile profile = manager.get(true);
    if (profile != null) {
      logger.info("Save profile: {}", profile.getTypedId());
      addCookie(response, Pac4jConstants.USER_PROFILE, profile.getTypedId());
    }
  }

  private String readCookie(final HttpServletRequest request, final String suffix) {
    final Cookie[] cookies = request.getCookies();
    for (final Cookie cookie : cookies) {
      final String name = cookie.getName();
      if (name.equals(PAC4J_SESSION_COOKIE_PREFIX + suffix)) {
        return cookie.getValue();
      }
    }
    return null;
  }

  protected void loadSession(final HttpServletRequest request, final HttpServletResponse response) {
    final HttpSession session = request.getSession();
    final String requestedUrl = readCookie(request, Pac4jConstants.REQUESTED_URL);
    logger.info("Load requestedUrl: {}", requestedUrl);
    if (requestedUrl != null) {
      session.setAttribute(Pac4jConstants.REQUESTED_URL, requestedUrl);
    }
    final String typedId = readCookie(request, Pac4jConstants.USER_PROFILE);
    logger.info("Load typedId: {}", typedId);
    if (typedId != null) {
      final J2EContext context = new J2EContext(request, response);
      final ProfileManager manager = new ProfileManager(context);
      final UserProfile profile = ProfileHelper.buildProfile(typedId, new HashMap<String, Object>());
      manager.save(true, profile);
    }
  }
}
