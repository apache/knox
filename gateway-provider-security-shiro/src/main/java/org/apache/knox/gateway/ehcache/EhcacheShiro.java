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
import org.apache.shiro.cache.CacheException;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class EhcacheShiro<K, V> implements Cache<K, V> {

    private final org.ehcache.Cache<K, V> cache;

    public EhcacheShiro(org.ehcache.Cache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("Cache argument cannot be null.");
        }

        this.cache = cache;
    }

    @Override
    public V get(K k) throws CacheException {
        if (k == null) {
            return null;
        }

        return cache.get(k);
    }

    @Override
    public V put(K k, V v) throws CacheException {
        V previousValue = null;

        while (true) {
            previousValue = cache.get(k);
            if (previousValue == null) {
                if (cache.putIfAbsent(k, v) == null) {
                    break;
                }
            } else {
                if (cache.replace(k, v) != null) {
                    break;
                }
            }
        }

        return previousValue;
    }

    @Override
    public V remove(K k) throws CacheException {
        V previousValue = null;

        while (true) {
            previousValue = cache.get(k);
            if (previousValue == null) {
                break;
            } else {
                if (cache.remove(k, previousValue)) {
                    break;
                }
            }
        }

        return previousValue;
    }

    @Override
    public void clear() throws CacheException {
        cache.clear();
    }

    @Override
    public int size() {
        Iterator<org.ehcache.Cache.Entry<K, V>> iterator = cache.iterator();
        int size = 0;
        while (iterator.hasNext()) {
            iterator.next();
            size++;
        }

        return size;
    }

    @Override
    public Set<K> keys() {
        return new EhcacheSetWrapper<K>(this, cache) {
            @Override
            public Iterator<K> iterator() {
                return new EhcacheIterator<K, V, K>(cache.iterator()) {
                    @Override
                    protected K getNext(Iterator<org.ehcache.Cache.Entry<K, V>> cacheIterator) {
                        return cacheIterator.next().getKey();
                    }
                };
            }
        };
    }

    @Override
    public Collection<V> values() {
        return new EhcacheCollectionWrapper<V>(this, cache) {
            @Override
            public Iterator<V> iterator() {
                return new EhcacheIterator<K, V, V>(cache.iterator()) {
                    @Override
                    protected V getNext(Iterator<org.ehcache.Cache.Entry<K, V>> cacheIterator) {
                        return cacheIterator.next().getValue();
                    }
                };
            }
        };
    }
}
