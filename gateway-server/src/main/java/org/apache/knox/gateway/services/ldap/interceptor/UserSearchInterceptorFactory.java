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
package org.apache.knox.gateway.services.ldap.interceptor;

import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;

import java.util.Map;

public class UserSearchInterceptorFactory implements KnoxLdapInterceptorFactory {
    public static final String TYPE = "backend";

    @Override
    public Interceptor create(GatewayConfig gatewayConfig, GatewayServices gatewayServices, String name, Map<String, String> interceptorConfig) throws Exception {
        return new UserSearchInterceptor(name, interceptorConfig);
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
