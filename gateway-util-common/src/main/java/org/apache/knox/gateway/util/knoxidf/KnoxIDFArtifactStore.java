/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util.knoxidf;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public abstract class KnoxIDFArtifactStore<T> {

    private final Cache<String, T> cache;

    protected KnoxIDFArtifactStore(long ttl) {
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl * 2, TimeUnit.MILLISECONDS).build();
    }

    public void put(String key, T value) {
        cache.put(key, value);
    }

    public T get(String key) {
        return cache.getIfPresent(key);
    }
}
