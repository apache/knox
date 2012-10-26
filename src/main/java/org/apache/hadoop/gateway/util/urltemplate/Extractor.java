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
package org.apache.hadoop.gateway.util.urltemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;

public class Extractor {

  public static Params extract( Template template, URI uri ) throws URISyntaxException {
    return new Extractor().extractParams( template, uri );
  }

  public Params extractParams( Template template, URI uri ) throws URISyntaxException {
    Params params = new Params();
    Template uriTemplate = Parser.parse( uri.toString() );
    extractSchemeParams( template, uriTemplate, params );
    extractAuthorityParams( template, uriTemplate, params );
    extractPathParams( template, uriTemplate, params );
    extractQueryParams( template, uriTemplate, params );
    extractFragmentParams( template, uriTemplate, params );
    return params;
  }

  private static void extractSchemeParams( Template extractTemplate, Template inputTemplate, Params params ) {
    extractSegmentParams( extractTemplate.getScheme(), inputTemplate.getScheme(), params );
  }

  private static void extractAuthorityParams( Template extractTemplate, Template inputTemplate, Params params ) {
    extractSegmentParams( extractTemplate.getUsername(), inputTemplate.getUsername(), params );
    extractSegmentParams( extractTemplate.getPassword(), inputTemplate.getPassword(), params );
    extractSegmentParams( extractTemplate.getHost(), inputTemplate.getHost(), params );
    extractSegmentParams( extractTemplate.getPort(), inputTemplate.getPort(), params );
  }

  private static void extractPathParams( Template extractTemplate, Template inputTemplate, Params params ) {
    List<PathSegment> inputPath = inputTemplate.getPath();
    int inputSegmentIndex=0, inputSegmentCount=inputPath.size();
    boolean matching = true;
    for( PathSegment extractSegment: extractTemplate.getPath() ) {
      if( matching && inputSegmentIndex < inputSegmentCount ) {
        PathSegment inputSegment = inputPath.get( inputSegmentIndex );
        if( extractSegment.matches( inputSegment ) ) {
          String paramName = extractSegment.getParamName();
          String paramValue = inputSegment.getValuePattern();
          if( paramName != null && paramName.length() > 0 ) {
            params.addValue( paramName, paramValue );
          }
          inputSegmentIndex++;
        } else {
          matching = false;
        }
      } else {
        String paramName = extractSegment.getParamName();
        if( paramName != null && paramName.length() > 0 ) {
          params.addName( paramName );
        }
      }
    }
  }

  private static void extractQueryParams( Template extractTemplate, Template inputTemplate, Params params ) {
    Iterator<QuerySegment> extractIterator = extractTemplate.getQuery().values().iterator();
    while( extractIterator.hasNext() ) {
      QuerySegment extractSegment = extractIterator.next();
      QuerySegment inputSegment = inputTemplate.getQuery().get( extractSegment.getQueryName() );
      extractSegmentParams( extractSegment, inputSegment, params );
    }
  }

  private static void extractFragmentParams( Template extractTemplate, Template inputTemplate, Params params ) {
    extractSegmentParams( extractTemplate.getFragment(), inputTemplate.getFragment(), params );
  }

  private static void extractSegmentParams( Segment extractSegment, Segment inputSegment, Params params ) {
    if( extractSegment != null && inputSegment != null ) {
      params.addValue( extractSegment.getParamName(), inputSegment.getValuePattern() );
    }
  }

}
