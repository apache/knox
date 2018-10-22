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
package org.apache.knox.gateway.dispatch;

import org.apache.knox.gateway.util.urltemplate.Params;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class DispatchParamResolver implements Params {

  private FilterConfig config;
  private HttpServletRequest request;

  DispatchParamResolver( FilterConfig config, HttpServletRequest request ) {
    this.config = config;
    this.request = request;
  }

  @Override
  public Set<String> getNames() {
    return Collections.emptySet();
  }

  @Override
  public List<String> resolve( String name ) {
    List<String> values = null;

    if( request !=  null ) {
      String[] array = request.getParameterValues( name );
      if( array != null ) {
        values = Arrays.asList( array );
        return values;
      }
    }

    if( config != null ) {
      String value = config.getInitParameter( name );
      if( value != null ) {
        values = new ArrayList<>( 1 );
        values.add( value );
        return values;
      }
    }

    return values;
  }
}
