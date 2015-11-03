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
package org.apache.hadoop.gateway.securequery;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.services.security.EncryptionResult;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.io.UnsupportedEncodingException;

public class SecureQueryEncryptProcessor
    implements UrlRewriteStepProcessor<SecureQueryEncryptDescriptor> {

  private static final String ENCRYPTED_PARAMETER_NAME = "_";

  private String clusterName;
  private CryptoService cryptoService = null;

  @Override
  public String getType() {
    return SecureQueryEncryptDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, SecureQueryEncryptDescriptor descriptor ) throws Exception {
    clusterName = environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    GatewayServices services = environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    cryptoService = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    //TODO: Need some way to get a reference to the keystore service and the encryption key in particular.
    Template url = context.getCurrentUrl();
    String str = url.toString();
    String path = str;
    String query = null;
    int index = str.indexOf( '?' );
    if( index >= 0 ) {
      path = str.substring( 0, index );
      if( index < str.length() ) {
        query = str.substring( index + 1 );
      }
    }
    if( query != null ) {
      query = encode( query );
      url = Parser.parseLiteral( path + "?" + ENCRYPTED_PARAMETER_NAME +"=" + query );
      context.setCurrentUrl( url );
    }
    return UrlRewriteStepStatus.SUCCESS;
  }

  @Override
  public void destroy() {
  }

  private String encode( String string ) throws UnsupportedEncodingException {
    EncryptionResult result = cryptoService.encryptForCluster(clusterName, "encryptQueryString", string.getBytes("UTF-8"));
    string = Base64.encodeBase64URLSafeString(result.toByteAray());
    return string;
  }
}
