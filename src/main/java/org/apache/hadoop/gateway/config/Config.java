/**
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
package org.apache.hadoop.gateway.config;

import java.util.*;

/**
 *
 */
public class Config implements Map<String,String> {

  private Map<String,String> params; // Decided to delegate vs inherit because of recursion issues that can occur during hash map expansion.
  private Config parent;
  private Map<String,Config> children;

  public Config( Config parent ) {
    this.parent = parent;
    this.params = new HashMap<String,String>();
    this.children = null;
  }

  public Config() {
    this( null );
  }

  public void addChild( Config child ) {
    if( children == null ) {
      children = new LinkedHashMap<String,Config>();
    }
    child.setParent( this );
    children.put( child.get("name"), child );
  }

  public Config getChild( String childName ) {
    return children.get( childName );
  }

  public Config getParent() {
    return parent;
  }

  public void setParent( Config parent ) {
    this.parent = parent;
  }

  public Map<String,Config> getChildren() {
    return children;
  }

  @Override
  public int size() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean containsKey( Object key ) {
    return false;
  }

  @Override
  public boolean containsValue( Object value ) {
    return false;
  }

  @Override
  public String get( Object key ) {
    String paramValue = params.get( key );
    if( paramValue == null && parent != null ) {
      paramValue = parent.get( key );
    }
    return paramValue;
  }

  @Override
  public String put( String key, String value ) {
    return params.put( key, value );
  }

  @Override
  public String remove( Object key ) {
    return params.remove( key );
  }

  @Override
  public void putAll( Map<? extends String, ? extends String> m ) {
    params.putAll( m );
  }

  @Override
  public void clear() {
    params.clear();
  }

  @Override
  public Set<String> keySet() {
    return params.keySet();
  }

  @Override
  public Collection<String> values() {
    return params.values();
  }

  @Override
  public Set<Entry<String, String>> entrySet() {
    return params.entrySet();
  }
}
