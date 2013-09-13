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
package org.apache.hadoop.gateway.hostmap.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.hadoop.gateway.hostmap.api.HostmapFunctionDescriptor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class HostmapFunctionProcessor
    implements UrlRewriteFunctionProcessor<HostmapFunctionDescriptor> {

  public static final String DESCRIPTOR_DEFAULT_FILE_NAME = "hostmap.txt";
  public static final String DESCRIPTOR_DEFAULT_LOCATION = "/WEB-INF/" + DESCRIPTOR_DEFAULT_FILE_NAME;

  private Map<String, String> inbound = new HashMap<String, String>();
  private Map<String, String> outbound = new HashMap<String, String>();

  @Override
  public String name() {
    return HostmapFunctionDescriptor.FUNCTION_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, HostmapFunctionDescriptor descriptor ) throws Exception {
    URL url = environment.getResource( DESCRIPTOR_DEFAULT_LOCATION );
    if( url != null ) {
      InputStream stream = url.openStream();
      BufferedReader reader = new BufferedReader( new InputStreamReader( stream ) );
      String line = reader.readLine();
      while( line != null ) {
        String[] lineSplit = line.split( "=" );
        if( lineSplit.length >= 2 ) {
          String[] externalSplit = lineSplit[ 0 ].split( "," );
          String[] internalSplit = lineSplit[ 1 ].split( "," );
          if( externalSplit.length >= 1 && internalSplit.length >= 1 ) {
            for( String external : externalSplit ) {
              inbound.put( external, internalSplit[ 0 ] );
            }
            for( String internal : internalSplit ) {
              outbound.put( internal, externalSplit[ 0 ] );
            }
          }
        }
        line = reader.readLine();
      }
      reader.close();
    }
  }

  @Override
  public void destroy() throws Exception {
  }

  @Override
  public String resolve( UrlRewriteContext context, String parameter ) throws Exception {
    String value;
    switch( context.getDirection() ) {
      case IN:
        value = inbound.get( parameter );
        break;
      case OUT:
        value = outbound.get( parameter );
        break;
      default:
        value = null;
    }
    if( value == null ) {
      value = parameter;
    }
//    System.out.println( "HOSTMAP: " + parameter + "->" + value );
    return value;
  }

}

