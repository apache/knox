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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.log4j.Logger;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Locale;

class NiFiRequestUtil {

  static HttpUriRequest modifyOutboundRequest(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest) throws IOException {
    // preserve trailing slash from inbound request in the outbound request
    if (inboundRequest.getPathInfo().endsWith("/")) {
      String[] split = outboundRequest.getURI().toString().split("\\?");
      if (!split[0].endsWith("/")) {
        outboundRequest = RequestBuilder.copy(outboundRequest).setUri(split[0] + "/" + (split.length == 2 ? "?" + split[1] : "")).build();
      }
    }
    // update the X-Forwarded-Context header to include the Knox-specific context path
    final Header originalXForwardedContextHeader = outboundRequest.getFirstHeader(NiFiHeaders.X_FORWARDED_CONTEXT);
    if (originalXForwardedContextHeader != null) {
      String xForwardedContextHeaderValue = originalXForwardedContextHeader.getValue();
      if (xForwardedContextHeaderValue != null && !xForwardedContextHeaderValue.isEmpty() && !xForwardedContextHeaderValue.contains("nifi-app")) {
        // Inspect the inbound request and outbound request to determine the additional context path from the rewrite
        // rules that needs to be added to the X-Forwarded-Context header to allow proper proxying to NiFi.
        //
        // NiFi does its own URL rewriting, and will not work with the context path provided by Knox
        // (ie, "/gateway/sandbox").
        //
        // For example, if Knox has a rewrite rule "*://*:*/**/nifi-app/{**}?{**}", "/nifi-app" needs to be added
        // to the existing value of the X-Forwarded-Context header, which ends up being "/gateway/sandbox/nifi-app".
        String inboundRequestPathInfo = inboundRequest.getPathInfo();
        String outboundRequestUriPath = outboundRequest.getURI().getPath();
        String outboundRequestUriPathNoTrailingSlash = StringUtils.removeEnd(outboundRequestUriPath, "/");
        String knoxRouteContext = null;
        int index = inboundRequestPathInfo.lastIndexOf(outboundRequestUriPathNoTrailingSlash);
        if (index >= 0) {
          knoxRouteContext = inboundRequestPathInfo.substring(0, index);
        } else {
          Logger.getLogger(NiFiRequestUtil.class.getName()).error(String.format(Locale.ROOT, "Unable to find index of %s in %s", outboundRequestUriPathNoTrailingSlash, inboundRequestPathInfo));
        }
        outboundRequest.setHeader(NiFiHeaders.X_FORWARDED_CONTEXT, xForwardedContextHeaderValue + knoxRouteContext);
      }
    }

    // NiFi requires the header "X-ProxiedEntitiesChain" to be set with the identity or identities of the authenticated requester.
    // The effective principal (identity) in the requester subject must be added to "X-ProxiedEntitiesChain".
    // If the request already has a populated "X-ProxiedEntitiesChain" header, the identities must be appended to it.
    // If the user proxied through Knox is anonymous, the "Anonymous" identity needs to be represented in X-ProxiedEntitiesChain
    // as empty angle brackets "<>".
    final Subject subject = SubjectUtils.getCurrentSubject();
    String effectivePrincipalName = SubjectUtils.getEffectivePrincipalName(subject);
    String proxiedEntitesChainHeader = inboundRequest.getHeader(NiFiHeaders.X_PROXIED_ENTITIES_CHAIN);
    if(proxiedEntitesChainHeader == null) {
      proxiedEntitesChainHeader = "";
    }
    outboundRequest.setHeader(NiFiHeaders.X_PROXIED_ENTITIES_CHAIN, proxiedEntitesChainHeader +
        String.format(Locale.ROOT, "<%s>", "anonymous".equalsIgnoreCase(effectivePrincipalName) ? "" : effectivePrincipalName));

    // Make sure headers named "Cookie" are removed from the request to NiFi, since NiFi does not use cookies.
    Header[] cookieHeaders = outboundRequest.getHeaders("Cookie");
    for (Header cookieHeader : cookieHeaders) {
      outboundRequest.removeHeader(cookieHeader);
    }
    return outboundRequest;
  }
}
