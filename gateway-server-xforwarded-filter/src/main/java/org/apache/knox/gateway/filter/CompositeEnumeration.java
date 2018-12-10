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
package org.apache.knox.gateway.filter;

import java.util.Enumeration;
import java.util.NoSuchElementException;

public class CompositeEnumeration<T> implements Enumeration<T> {

  private int index;
  private Enumeration<T>[] array;

  @SafeVarargs
  public CompositeEnumeration(Enumeration<T>... enumerations) {
    if( enumerations == null ) {
      throw new IllegalArgumentException( "enumerations==null" );
    }
    this.array = enumerations;
  }

  @Override
  public boolean hasMoreElements() {
    while( array.length > 0 && index < array.length ) {
      if( array[index].hasMoreElements() ) {
        return true;
      } else {
        index++;
      }
    }
    return false;
  }

  @Override
  public T nextElement() {
    if( hasMoreElements() ) {
      return array[index].nextElement();
    } else {
      throw new NoSuchElementException();
    }
  }

}
