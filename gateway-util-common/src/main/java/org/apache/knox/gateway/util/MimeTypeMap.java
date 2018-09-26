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
package org.apache.knox.gateway.util;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import java.util.HashMap;
import java.util.Map;

public class MimeTypeMap<V> {

  private Map<String,V> map;

  public MimeTypeMap() {
    map = new HashMap<>();
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey( Object key ) {
    return map.containsKey( key );
  }

  public boolean containsValue( Object value ) {
    return map.containsValue( value );
  }

  public V get( Object key ) {
    MimeType type;
    V value = null;
    if( key instanceof String ) {
      try {
        type = new MimeType( (String)key );
      } catch( MimeTypeParseException e ) {
        type = null;
      }
    } else if( key instanceof MimeType ) {
      type = (MimeType)key;
    } else {
      type = null;
    }
    if( type != null ) {
      String priType = type.getPrimaryType();
      value = map.get( type.getBaseType() );
      if( value == null ) {
        try {
          type.setPrimaryType( "*" );
          value = map.get( type.getBaseType() );
        } catch( MimeTypeParseException e ) {
          // Should be impossible, will return null.
        }
      }
      if( value == null ) {
        try {
          type.setPrimaryType( priType );
          type.setSubType( "*" );
          value = map.get( type.getBaseType() );
        } catch( MimeTypeParseException e ) {
          // Should be impossible, will return null.
        }
      }
      if( value == null ) {
        try {
          type.setPrimaryType( "*" );
          type.setSubType( "*" );
          value = map.get( type.getBaseType() );
        } catch( MimeTypeParseException e ) {
          // Should be impossible, will return null.
        }
      }
    }
    return value;
  }

  public V put( MimeType key, V value ) {
    return map.put( key.getBaseType(), value );
  }

  public V remove( Object key ) {
    return map.remove( key );
  }

  public void clear() {
    map.clear();
  }

}
