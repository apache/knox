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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;

  /**
   * This is a specialized PassAllHeadersDispatch dispatch that decodes the URL before
   * dispatch. Ambari Views do not work with the query string percent encoded. Other
   * UIs may require this at some point as well.
   */
public class PassAllHeadersNoEncodingDispatch extends PassAllHeadersDispatch {
  public URI getDispatchUrl( HttpServletRequest request) {
    String base = request.getRequestURI();
    StringBuffer str = new StringBuffer();
    str.append( base );
    String query = request.getQueryString();
    if (query != null) {
      try {
        query = URLDecoder.decode(query, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        // log
      }
      str.append( '?' );
      str.append( query );
    }
    encodeUnwiseCharacters(str);
    URI uri = URI.create( str.toString() );
    return uri;
  }
}
