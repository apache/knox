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
package org.apache.hadoop.gateway.util.uritemplate;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

public class Extractor {

  public Params extract( Template template, URI uri ) {
    Parser parser = new Parser();
    Params params = new Params();
    Template uriTemplate = parser.parse( uri.toString() );
    extractPathParams( template, uriTemplate, params );
    extractQueryParams( template, uriTemplate, params );
    return params;
  }

  private void extractPathParams( Template extractTemplate, Template inputTemplate, Params params ) {
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

  private void extractQueryParams( Template extractTemplate, Template inputTemplate, Params params ) {
    boolean matching = true;
    Iterator<QuerySegment> extractIterator = extractTemplate.getQuery().values().iterator();
    while( extractIterator.hasNext() ) {
      QuerySegment extractSegment = extractIterator.next();
      QuerySegment inputSegment = inputTemplate.getQuery().get( extractSegment.getQueryName() );
      params.addValue( extractSegment.getParamName(), inputSegment.getValuePattern() );
    }
  }

}
