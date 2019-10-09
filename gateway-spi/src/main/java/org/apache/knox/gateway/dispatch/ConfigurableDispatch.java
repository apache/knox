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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extends DefaultDispatch to:
 *   make request/response exclude headers configurable
 *   make url encoding configurable
 */
public class ConfigurableDispatch extends DefaultDispatch {
  private Set<String> requestExcludeHeaders = super.getOutboundRequestExcludeHeaders();
  private Set<String> responseExcludeHeaders = super.getOutboundResponseExcludeHeaders();
  private Set<String> responseExcludeSetCookieHeaderDirectives = super.getOutboundResponseExcludedSetCookieHeaderDirectives();
  private Boolean removeUrlEncoding = false;

  private Set<String> convertCommaDelimitedHeadersToSet(String headers) {
    return headers == null ?  Collections.emptySet(): new HashSet<>(Arrays.asList(headers.split(",")));
  }

  @Configure
  protected void setRequestExcludeHeaders(@Default(" ") String headers) {
    if(!" ".equals(headers)) {
      this.requestExcludeHeaders = convertCommaDelimitedHeadersToSet(headers);
    }
  }

  @Configure
  protected void setResponseExcludeHeaders(@Default(" ") String headers) {
    if (!" ".equals(headers)) {
      final Set<String> headerSet = convertCommaDelimitedHeadersToSet(headers);
      populateSetCookieHeaderDirectiveExlusions(headerSet);
      populateHttpHeaderExlusionsOtherThanSetCookie(headerSet);
    }
  }

  private void populateSetCookieHeaderDirectiveExlusions(final Set<String> headerSet) {
    final Optional<String> setCookieHeader = headerSet.stream().filter(s -> s.startsWith(SET_COOKIE)).findFirst();
    if (setCookieHeader.isPresent()) {
      final String[] setCookieHeaderParts = setCookieHeader.get().split(":");
      responseExcludeSetCookieHeaderDirectives = setCookieHeaderParts.length > 1
          ? new HashSet<>(Arrays.asList(setCookieHeaderParts[1].split(";"))).stream().map(e -> e.trim()).collect(Collectors.toSet())
              : Collections.emptySet();
    } else {
      responseExcludeSetCookieHeaderDirectives = Collections.emptySet();
    }
  }

  private void populateHttpHeaderExlusionsOtherThanSetCookie(final Set<String> headerSet) {
    final Set<String> excludedHeadersOthenThanSetCookie = headerSet.stream().filter(s -> !s.startsWith(SET_COOKIE)).collect(Collectors.toSet());
    if (!excludedHeadersOthenThanSetCookie.isEmpty()) {
      this.responseExcludeHeaders = excludedHeadersOthenThanSetCookie;
    }
  }


  @Configure
  protected void setRemoveUrlEncoding(@Default("false") String removeUrlEncoding) {
    this.removeUrlEncoding = Boolean.parseBoolean(removeUrlEncoding);
  }

  @Override
  public Set<String> getOutboundResponseExcludeHeaders() {
    return responseExcludeHeaders == null ? Collections.emptySet() : responseExcludeHeaders;
  }

  @Override
  public Set<String> getOutboundResponseExcludedSetCookieHeaderDirectives() {
    return responseExcludeSetCookieHeaderDirectives == null ? Collections.emptySet() : responseExcludeSetCookieHeaderDirectives;
  }

  @Override
  public Set<String> getOutboundRequestExcludeHeaders() {
    return requestExcludeHeaders == null ? Collections.emptySet() : requestExcludeHeaders;
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
