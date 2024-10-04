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
package org.apache.knox.gateway.shirorealm;

import org.apache.shiro.cache.Cache;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KnoxCacheManagerTest {

    @Test
    public void testConfigFileManipulation() {
        KnoxCacheManager cacheManager = new KnoxCacheManager();
        cacheManager.setCacheManagerConfigFile("config.xml");

        assertEquals("config.xml", cacheManager.getCacheManagerConfigFile());
    }

    @Test
    public void testGetCache() throws Exception {
        KnoxCacheManager cacheManager = new KnoxCacheManager();
        Cache<Object, Object> cache = cacheManager.getCache("cache");

        cache.put("testK", "testV");
        assertEquals("testV", cache.get("testK"));

        cacheManager.destroy();
        assertNull(cacheManager.getCacheManager());
    }

}