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

import org.apache.commons.collections.MapUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import org.apache.knox.gateway.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Optional;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Extends DefaultDispatch to:
 *   make request/response exclude headers configurable
 *   make url encoding configurable
 */
public class ConfigurableDispatch extends DefaultDispatch {
  private Set<String> requestExcludeHeaders = super.getOutboundRequestExcludeHeaders();
  private Set<String> responseExcludeHeaders = super.getOutboundResponseExcludeHeaders();
  private Map<String, String> requestAppendHeaders = Collections.emptyMap();
  private Set<String> responseExcludeSetCookieHeaderDirectives = super.getOutboundResponseExcludedSetCookieHeaderDirectives();
  private Boolean removeUrlEncoding = false;

  private Set<String> convertCommaDelimitedHeadersToSet(String headers) {
    return headers == null ?  Collections.emptySet(): new HashSet<>(Arrays.asList(headers.split("\\s*,\\s*")));
  }

  /**
   * This function converts header string to a Map(String, String)
   * Header String format: a:b,c:d --> this will translate to Map({a=b,c=d})
   */
  private Map<String, String> convertCommaDelimitedHeadersToMap(String headers) {
    if(null == headers){
      return Collections.emptyMap();
    }
    try {
      Map<String, String> extraHeaders = new HashMap<>();
      Arrays.stream(headers.split("\\s*;\\s*"))
              .forEach(keyValuePair -> extraHeaders.putIfAbsent(keyValuePair.split("\\s*:\\s*")[0], keyValuePair.split("\\s*:\\s*")[1]));
      return extraHeaders;
    } catch (Exception e) {
      auditor.audit("deserialize headers", headers, "header",
              ActionOutcome.WARNING, "Extra Headers are skipped because of "+e.getMessage());
      return Collections.emptyMap();
    }
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

  @Configure
  protected void setRequestAppendHeaders(@Default(" ") String extraHeaders) {
    if(!" ".equals(extraHeaders)) {
      this.requestAppendHeaders = convertCommaDelimitedHeadersToMap(extraHeaders);
    }
  }

  private void populateSetCookieHeaderDirectiveExlusions(final Set<String> headerSet) {
    final Optional<String> setCookieHeader = headerSet.stream().filter(s -> StringUtils.startsWithIgnoreCase(s, SET_COOKIE)).findFirst();
    if (setCookieHeader.isPresent()) {
      final String[] setCookieHeaderParts = setCookieHeader.get().split(":");
      responseExcludeSetCookieHeaderDirectives = setCookieHeaderParts.length > 1
          ? new HashSet<>(Arrays.asList(setCookieHeaderParts[1].split(";"))).stream().map(e -> e.trim()).collect(Collectors.toSet())
          : EXCLUDE_SET_COOKIES_DEFAULT;
    } else {
      /* Exclude headers list is defined but we don't have set-cookie in the list,
      by default prevent these cookies from leaking */
      responseExcludeSetCookieHeaderDirectives = EXCLUDE_SET_COOKIES_DEFAULT;
    }
  }

  private void populateHttpHeaderExlusionsOtherThanSetCookie(final Set<String> headerSet) {
    final Set<String> excludedHeadersOthenThanSetCookie = headerSet.stream().filter(s -> !StringUtils.startsWithIgnoreCase(s, SET_COOKIE)).collect(Collectors.toSet());
    if (!excludedHeadersOthenThanSetCookie.isEmpty()) {
      this.responseExcludeHeaders = excludedHeadersOthenThanSetCookie;
    }
  }

  @Configure
  protected void setRemoveUrlEncoding(@Default("false") String removeUrlEncoding) {
    this.removeUrlEncoding = Boolean.parseBoolean(removeUrlEncoding);
  }

  @Override
  public void copyRequestHeaderFields(HttpUriRequest outboundRequest,
                                      HttpServletRequest inboundRequest) {
    super.copyRequestHeaderFields(outboundRequest, inboundRequest);

    //If there are some headers to be appended, append them
    Map<String, String> extraHeaders = getOutboundRequestAppendHeaders();
    if(MapUtils.isNotEmpty(extraHeaders)){
      extraHeaders.forEach(outboundRequest::addHeader);
    }
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

  public Map<String, String> getOutboundRequestAppendHeaders() {
    return requestAppendHeaders == null ? Collections.emptyMap() : requestAppendHeaders;
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
