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
package org.apache.knox.gateway.util.urltemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MockParams implements Params {

  private Map<String,List<String>> map = new LinkedHashMap<>();

  @Override
  public Set<String> getNames() {
    return map.keySet();
  }

  private List<String> getOrAddValues( String name ) {
    List<String> values = resolve( name );
    if( values == null ) {
      values = new ArrayList<>( 1 );
      map.put( name, values );
    }
    return values;
  }

  public void addValue( String name, String value ) {
    List<String> values = getOrAddValues( name );
    values.add( value );
  }

  public void insertValue( String name, String value ) {
    List<String> values = getOrAddValues( name );
    values.add( 0, value );
  }

  @Override
  public List<String> resolve( String name ) {
    return map.get( name );
  }

}
