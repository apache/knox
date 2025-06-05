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
package org.apache.knox.gateway.ha.dispatch;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.ArrayList;
import java.util.List;

public class StickySessionCookieRemovedRequest extends HttpServletRequestWrapper {
    private final Cookie[] cookies;

    StickySessionCookieRemovedRequest(String cookieName, HttpServletRequest request) {
        super(request);
        this.cookies = filterCookies(cookieName, request.getCookies());
    }

    private Cookie[] filterCookies(String cookieName, Cookie[] cookies) {
        if (super.getCookies() == null) {
            return null;
        }
        List<Cookie> cookiesInternal = new ArrayList<>();
        for (Cookie cookie : cookies) {
            if (!cookieName.equals(cookie.getName())) {
                cookiesInternal.add(cookie);
            }
        }
        return cookiesInternal.toArray(new Cookie[0]);
    }

    @Override
    public Cookie[] getCookies() {
        return cookies;
    }
}
