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
package org.apache.knox.gateway.filter.rewrite.api;

import javax.servlet.ServletContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class UrlRewriteServletEnvironment implements UrlRewriteEnvironment {

  private ServletContext context;

  public UrlRewriteServletEnvironment( ServletContext context ) {
    this.context = context;
  }

  @Override
  public URL getResource( String name ) throws MalformedURLException {
    URL url = context.getResource( name );
    return url;
  }

  @Override
  public <T> T getAttribute( String name ) {
    T attribute = (T)context.getAttribute( name );
    return attribute;
  }

  @Override
  public List<String> resolve( String name ) {
    List<String> values = null;
    String value = context.getInitParameter( name );
    if( value != null ) {
      values = Arrays.asList( value );
    }
    return values;
  }

}
