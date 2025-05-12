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
package org.apache.knox.gateway.util;

import java.util.HashMap;
import java.util.Map;

public class SetCookieHeader {
    private final String name;
    private final String value;
    private final Map<String, String> attributes = new HashMap<>();

    private String path;
    private String domain;
    private int maxAge = -1;
    private boolean httpOnly;
    private boolean secure;
    private String sameSite;

    public SetCookieHeader(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getPath() {
        return path;
    }

    public String getDomain() {
        return domain;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public boolean isSecure() {
        return secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public String getAttribute(String name) {
        return attributes.get(name);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(name);
        sb.append('=').append(value);

        if(path != null) {
            sb.append("; Path=").append(path);
        }
        if(domain != null) {
            sb.append("; Domain=").append(domain);
        }
        if(maxAge != -1) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if(httpOnly) {
            sb.append("; HttpOnly");
        }
        if(secure) {
            sb.append("; Secure");
        }
        if(sameSite != null) {
            sb.append("; SameSite=").append(sameSite);
        }

        for(Map.Entry<String, String> entry : attributes.entrySet()) {
            sb.append("; ").append(entry.getKey()).append('=').append(entry.getValue());
        }

        return sb.toString();
    }
}
