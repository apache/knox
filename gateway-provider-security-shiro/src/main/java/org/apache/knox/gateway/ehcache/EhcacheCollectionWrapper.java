/*
 * Copyright Terracotta, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.knox.gateway.ehcache;

import org.apache.shiro.cache.Cache;

import java.util.AbstractCollection;
import java.util.Collection;

abstract class EhcacheCollectionWrapper<E> extends AbstractCollection<E> {

    private final Cache shiroCache;

    private final org.ehcache.Cache ehcacheCache;

    EhcacheCollectionWrapper(Cache shiroCache, org.ehcache.Cache ehcacheCache) {
        this.shiroCache = shiroCache;
        this.ehcacheCache = ehcacheCache;
    }

    @Override
    public int size() {
        return shiroCache.size();
    }

    @Override
    public boolean isEmpty() {
        return !ehcacheCache.iterator().hasNext();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("addAll");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("remove");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("removeAll");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("retainAll");
    }
}
