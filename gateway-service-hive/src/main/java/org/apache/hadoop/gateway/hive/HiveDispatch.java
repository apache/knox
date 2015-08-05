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
package org.apache.hadoop.gateway.hive;

import org.apache.hadoop.gateway.config.Configure;
import org.apache.hadoop.gateway.dispatch.DefaultDispatch;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.apache.hadoop.gateway.security.SubjectUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.DefaultHttpClient;

import javax.security.auth.Subject;
import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;

/**
 * This specialized dispatch provides Hive specific features to the
 * default HttpClientDispatch.
 */
public class HiveDispatch extends DefaultDispatch {
  private static final String PASSWORD_PLACEHOLDER = "*";
  private boolean basicAuthPreemptive = false;
  private boolean kerberos = false;
  private static final EmptyJaasCredentials EMPTY_JAAS_CREDENTIALS = new EmptyJaasCredentials();

  @Override
  public void init() {
    super.init();
  }

  protected void addCredentialsToRequest(HttpUriRequest request) {
    if( isBasicAuthPreemptive() ) {
      String principal = SubjectUtils.getCurrentEffectivePrincipalName();
      if( principal != null ) {

        UsernamePasswordCredentials credentials =
            new UsernamePasswordCredentials( principal, PASSWORD_PLACEHOLDER );

        request.addHeader(BasicScheme.authenticate(credentials,"US-ASCII",false));
      }
    }
  }

  @Configure
  public void setBasicAuthPreemptive( boolean basicAuthPreemptive ) {
    this.basicAuthPreemptive = basicAuthPreemptive;
  }

  public boolean isBasicAuthPreemptive() {
    return basicAuthPreemptive;
  }

  public boolean isKerberos() {
    return kerberos;
  }

  @Configure
  public void setKerberos(boolean kerberos) {
    this.kerberos = kerberos;
  }

  protected HttpResponse executeKerberosDispatch(HttpUriRequest outboundRequest,
      HttpClient httpClient) throws IOException {
    DefaultHttpClient client = new DefaultHttpClient();
    SPNegoSchemeFactory spNegoSF = new SPNegoSchemeFactory(
          /* stripPort */true);
    // spNegoSF.setSpengoGenerator(new BouncySpnegoTokenGenerator());
    client.getAuthSchemes().register(AuthPolicy.SPNEGO, spNegoSF);
    client.getCredentialsProvider().setCredentials(
        new AuthScope(/* host */null, /* port */-1, /* realm */null),
        EMPTY_JAAS_CREDENTIALS);
    return client.execute(outboundRequest);
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

