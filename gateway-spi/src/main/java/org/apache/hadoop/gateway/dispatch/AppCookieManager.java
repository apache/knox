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
package org.apache.hadoop.gateway.dispatch;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;

import org.apache.hadoop.gateway.SpiGatewayMessages;
import org.apache.hadoop.gateway.audit.api.Action;
import org.apache.hadoop.gateway.audit.api.ActionOutcome;
import org.apache.hadoop.gateway.audit.api.AuditServiceFactory;
import org.apache.hadoop.gateway.audit.api.Auditor;
import org.apache.hadoop.gateway.audit.api.ResourceType;
import org.apache.hadoop.gateway.audit.log4j.audit.AuditConstants;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * Handles SPNego authentication as a client of hadoop service, caches
 * hadoop.auth cookie returned by hadoop service on successful SPNego
 * authentication. Refreshes hadoop.auth cookie on demand if the cookie has
 * expired.
 * 
 */
public class AppCookieManager {

  static final String HADOOP_AUTH = "hadoop.auth";
  private static final String HADOOP_AUTH_EQ = "hadoop.auth=";
  private static final String SET_COOKIE = "Set-Cookie";

  private static SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor( AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );
  private static final EmptyJaasCredentials EMPTY_JAAS_CREDENTIALS = new EmptyJaasCredentials();

  String appCookie;

  /**
   * Utility method to excerise AppCookieManager directly
   * @param args element 0 of args should be a URL to hadoop service protected by SPengo
   * @throws IOException in case of errors
   */
  public static void main(String[] args) throws IOException {
    HttpUriRequest outboundRequest = new HttpGet(args[0]);
    new AppCookieManager().getAppCookie(outboundRequest, false);
  }

  public AppCookieManager() {
  }

  /**
   * Fetches hadoop.auth cookie from hadoop service authenticating using SpNego
   * 
   * @param outboundRequest
   *          out going request
   * @param refresh
   *          flag indicating whether to refresh the cached cookie
   * @return hadoop.auth cookie from hadoop service authenticating using SpNego
   * @throws IOException
   *           in case of errors
   */
  public String getAppCookie(HttpUriRequest outboundRequest, boolean refresh)
      throws IOException {

    URI uri = outboundRequest.getURI();
    String scheme = uri.getScheme();
    String host = uri.getHost();
    int port = uri.getPort();
    if (!refresh) {
      if (appCookie != null) {
        return appCookie;
      }
    }

    DefaultHttpClient client = new DefaultHttpClient();
    SPNegoSchemeFactory spNegoSF = new SPNegoSchemeFactory(
    /* stripPort */true);
    // spNegoSF.setSpengoGenerator(new BouncySpnegoTokenGenerator());
    client.getAuthSchemes().register(AuthPolicy.SPNEGO, spNegoSF);
    client.getCredentialsProvider().setCredentials(
        new AuthScope(/* host */null, /* port */-1, /* realm */null),
        EMPTY_JAAS_CREDENTIALS);

    clearAppCookie();
    String hadoopAuthCookie = null;
    HttpResponse httpResponse = null;
    try {
      HttpHost httpHost = new HttpHost(host, port, scheme);
      HttpRequest httpRequest = createKerberosAuthenticationRequest( outboundRequest );
      httpResponse = client.execute(httpHost, httpRequest);
      Header[] headers = httpResponse.getHeaders(SET_COOKIE);
      hadoopAuthCookie = getHadoopAuthCookieValue(headers);
      EntityUtils.consume( httpResponse.getEntity() );
      if (hadoopAuthCookie == null) {
        LOG.failedSPNegoAuthn(uri.toString());
        auditor.audit( Action.AUTHENTICATION, uri.toString(), ResourceType.URI, ActionOutcome.FAILURE );
        throw new IOException(
            "SPNego authn failed, can not get hadoop.auth cookie");
      }
    } finally {
      if (httpResponse != null) {
        HttpEntity entity = httpResponse.getEntity();
        if (entity != null) {
          entity.getContent().close();
        }
      }

    }
    LOG.successfulSPNegoAuthn(uri.toString());
    auditor.audit( Action.AUTHENTICATION, uri.toString(), ResourceType.URI, ActionOutcome.SUCCESS);
    hadoopAuthCookie = HADOOP_AUTH_EQ + quote(hadoopAuthCookie);
    setAppCookie(hadoopAuthCookie);
    return appCookie;
  }

  protected HttpRequest createKerberosAuthenticationRequest( HttpUriRequest userRequest ) {
    HttpRequest authRequest = new HttpOptions( userRequest.getURI().getPath() );
    return authRequest;
  }

  /**
   * Returns the cached app cookie
   * 
   * @return the cached app cookie, can be null
   */
  public String getCachedAppCookie() {
    return appCookie;
  }
  
  private void setAppCookie(String appCookie) {
    this.appCookie = appCookie;
  }

  private void clearAppCookie() {
    appCookie = null;
  }
  
  static String quote(String s) {
    return s == null ? s : "\"" + s + "\"";
  }

  static String getHadoopAuthCookieValue(Header[] headers) {
    if (headers == null) {
      return null;
    }
    for (Header header : headers) {
      HeaderElement[] elements = header.getElements();
      for (HeaderElement element : elements) {
        String cookieName = element.getName();
        if (cookieName.equals(HADOOP_AUTH)) {
          if (element.getValue() != null) {
            String trimmedVal = element.getValue().trim();
            if (!trimmedVal.isEmpty()) {
              return trimmedVal;
            }
          }
        }
      }
    }
    return null;
  }

  private static class EmptyJaasCredentials implements Credentials {

    public String getPassword() {
      return null;
    }

    public Principal getUserPrincipal() {
      return null;
    }

  }

}
