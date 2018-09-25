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
package org.apache.knox.gateway.dispatch;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;

/**
 * Dispatch which decodes the outgoing URLs (to services).
 * This is useful in cases where the url is picked up
 * from the query parameter and is already encoded.
 *
 * @since 1.1.0
 */
public class URLDecodingDispatch extends DefaultDispatch {

  public URLDecodingDispatch() {
    super();
  }

  @Override
  public URI getDispatchUrl(final HttpServletRequest request) {
    String decoded;

    try {
      decoded = URLDecoder.decode(request.getRequestURL().toString(), "UTF-8" );
    } catch (final Exception e) {
      /* fall back in case of exception */
      decoded = request.getRequestURL().toString();
    }

    final StringBuffer str = new StringBuffer(decoded);
    final String query = request.getQueryString();
    if ( query != null ) {
      str.append('?');
      str.append(query);
    }
    final URI url = URI.create(str.toString());
    return url;
  }
}
