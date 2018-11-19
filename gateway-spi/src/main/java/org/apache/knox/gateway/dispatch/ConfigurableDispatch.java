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

import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Extends DefaultDispatch to:
 *   make request/response exclude headers configurable
 *   make url encoding configurable
 */
public class ConfigurableDispatch extends DefaultDispatch {
  private Set<String> requestExcludeHeaders = super.getOutboundRequestExcludeHeaders();
  private Set<String> responseExcludeHeaders = super.getOutboundResponseExcludeHeaders();
  private Boolean removeUrlEncoding = false;

  private Set<String> handleCommaSeparatedHeaders(String headers) {
    if(headers != null) {
      return new HashSet<>(Arrays.asList(headers.split(",")));
    }
    return Collections.emptySet();
  }

  @Configure
  protected void setRequestExcludeHeaders(@Default(" ") String headers) {
    if(!" ".equals(headers)) {
      this.requestExcludeHeaders = handleCommaSeparatedHeaders(headers);
    }
  }

  @Configure
  protected void setResponseExcludeHeaders(@Default(" ") String headers) {
    if(!" ".equals(headers)) {
      this.responseExcludeHeaders = handleCommaSeparatedHeaders(headers);
    }
  }

  @Configure
  protected void setRemoveUrlEncoding(@Default("false") String removeUrlEncoding) {
    this.removeUrlEncoding = Boolean.parseBoolean(removeUrlEncoding);
  }

  @Override
  public Set<String> getOutboundResponseExcludeHeaders() {
    return responseExcludeHeaders;
  }

  @Override
  public Set<String> getOutboundRequestExcludeHeaders() {
    return requestExcludeHeaders;
  }

  public boolean getRemoveUrlEncoding() {
    return removeUrlEncoding;
  }

  @Override
  public URI getDispatchUrl(HttpServletRequest request) {
    if (getRemoveUrlEncoding()) {
      String base = request.getRequestURI();
      StringBuffer str = new StringBuffer();
      str.append(base);
      String query = request.getQueryString();
      if (query != null) {
        try {
          query = URLDecoder.decode(query, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
          // log
        }
        str.append('?');
        str.append(query);
      }
      encodeUnwiseCharacters(str);
      return URI.create(str.toString());
    }

    return super.getDispatchUrl(request);
  }
}
