/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.auth;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class DoAsQueryParameterInterceptor implements Interceptor {

    private final String userName;
    private static final String DO_AS_PRINCIPAL_PARAM = "doAs";

    public DoAsQueryParameterInterceptor(String userName) {
        this.userName = userName;
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        HttpUrl url = chain.request().url().newBuilder()
                .addQueryParameter(DO_AS_PRINCIPAL_PARAM, userName)
                .build();
        Request request = chain.request().newBuilder()
                .url(url)
                .build();
        return chain.proceed(request);
    }
}
