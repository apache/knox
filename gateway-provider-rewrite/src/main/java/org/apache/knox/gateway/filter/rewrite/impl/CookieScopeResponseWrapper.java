/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.filter.GatewayResponseWrapper;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public class CookieScopeResponseWrapper extends GatewayResponseWrapper {
    private static final String SET_COOKIE = "Set-Cookie";
    private static final String COOKIE_PATH = "Path=/";

    private final String scopePath;

    public CookieScopeResponseWrapper(HttpServletResponse response, String gatewayPath) {
        super(response);
        this.scopePath = COOKIE_PATH + generateIfValidSegment(gatewayPath);
    }

    public CookieScopeResponseWrapper(HttpServletResponse response, String gatewayPath,
                                      String topologyName) {
        super(response);
        this.scopePath = COOKIE_PATH + generateIfValidSegment(gatewayPath) +
                             generateIfValidSegment(topologyName);
    }

    @Override
    public void addHeader(String name, String value) {
        if (SET_COOKIE.equals(name)) {
            String updatedCookie;
            if (hasCookiePathAttribute(value)) {
                updatedCookie = value.replaceAll("(?i)" + COOKIE_PATH, scopePath);
            } else {
                // append the scope path
                updatedCookie = String.format(Locale.ROOT, "%s %s;", value, scopePath);
            }
            super.addHeader(name, updatedCookie);
        } else {
            super.addHeader(name, value);
        }
    }

    private boolean hasCookiePathAttribute(String value) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(COOKIE_PATH.toLowerCase(Locale.ROOT));
    }

    @Override
    public OutputStream getRawOutputStream() throws IOException {
        return getResponse().getOutputStream();
    }

    private String generateIfValidSegment(String pathSegment){
        if(pathSegment == null || pathSegment.isEmpty() || "/".equals(pathSegment)){
            return "";
        }
        return pathSegment + "/";
    }
}
