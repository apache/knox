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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;

abstract class EhcacheSetWrapper<E> extends AbstractSet<E> {

    private final Collection<E> delegate;

    EhcacheSetWrapper(Cache shiroCache, org.ehcache.Cache ehcacheCache) {
        delegate = new EhcacheCollectionWrapper<E>(shiroCache, ehcacheCache) {
            @Override
            public Iterator<E> iterator() {
                throw new IllegalStateException("Should not use this iterator");
            }
        };
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }
}
