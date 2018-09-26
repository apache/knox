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
package org.apache.knox.gateway.services.hostmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class FileBasedHostMapper implements HostMapper {

  private Map<String, String> inbound = new HashMap<>();
  private Map<String, String> outbound = new HashMap<>();

  public FileBasedHostMapper( URL url ) throws IOException {
    if( url != null ) {
      InputStream stream = url.openStream();
      BufferedReader reader = new BufferedReader( new InputStreamReader( stream, StandardCharsets.UTF_8 ) );
      String line = reader.readLine();
      while( line != null ) {
        String[] lineSplit = line.split( "=" );
        if( lineSplit.length >= 2 ) {
          String[] externalSplit = lineSplit[ 0 ].split( "," );
          String[] internalSplit = lineSplit[ 1 ].split( "," );
          if( externalSplit.length >= 1 && internalSplit.length >= 1 ) {
            for( String external : externalSplit ) {
              inbound.put( external.trim(), internalSplit[ 0 ].trim() );
            }
            for( String internal : internalSplit ) {
              outbound.put( internal.trim(), externalSplit[ 0 ].trim() );
            }
          }
        }
        line = reader.readLine();
      }
      reader.close();
    }
  }

  /* (non-Javadoc)
   * @see HostMapper#resolveInboundHostName(java.lang.String)
   */
  @Override
  public String resolveInboundHostName( String hostName ) {
    String resolvedHostName = inbound.get( hostName );
    if( resolvedHostName == null ) {
      resolvedHostName = hostName;
    }
    return resolvedHostName;
  }

  /* (non-Javadoc)
   * @see HostMapper#resolveOutboundHostName(java.lang.String)
   */
  @Override
  public String resolveOutboundHostName( String hostName ) {
    String resolvedHostName = outbound.get( hostName );
    if( resolvedHostName == null ) {
      resolvedHostName = hostName;
    }
    return resolvedHostName;
  }
}
