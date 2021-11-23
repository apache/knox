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
package org.apache.knox.gateway.websockets;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

public class WebSocketFilterConfig implements FilterConfig {
    private Map<String,String> params;

    WebSocketFilterConfig(Map<String, String> params){
        this.params = params;
    }
    @Override
    public String getFilterName() {
        return "WebSocket";
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public String getInitParameter(String s) {
        String value = null;
        if (params != null) {
            value = params.get(s);
        }
        return value;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        Enumeration<String> names = null;
        if( params != null ) {
            names = Collections.enumeration( params.keySet() );
        }
        return names;
    }
}
