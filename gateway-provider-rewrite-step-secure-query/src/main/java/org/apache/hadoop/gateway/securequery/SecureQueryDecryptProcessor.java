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
import org.apache.hadoop.gateway.util.urltemplate.Builder;
import org.apache.hadoop.gateway.util.urltemplate.Query;
import org.apache.hadoop.gateway.util.urltemplate.Template;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class SecureQueryDecryptProcessor implements UrlRewriteStepProcessor<SecureQueryDecryptDescriptor> {

  private static final String ENCRYPTED_PARAMETER_NAME = "_";

  private String clusterName;
  private CryptoService cryptoService;

  @Override
  public String getType() {
    return SecureQueryDecryptDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, SecureQueryDecryptDescriptor descriptor ) throws Exception {
    clusterName = environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    GatewayServices services = environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    cryptoService = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    //TODO: Need some way to get a reference to the keystore service and the encryption key in particular.
    Template currUrl = context.getCurrentUrl();
    Builder newUrl = new Builder( currUrl );
    Map<String,Query> map = newUrl.getQuery();
    Query query = map.remove( ENCRYPTED_PARAMETER_NAME );
    if( query != null ) {
      String value = query.getFirstValue().getPattern();
      value = decode( value );
      StringTokenizer outerParser = new StringTokenizer( value, "&" );
      while( outerParser.hasMoreTokens() ) {
        String pair = outerParser.nextToken();
        StringTokenizer innerParser = new StringTokenizer( pair, "=" );
        if( innerParser.hasMoreTokens() ) {
          String paramName = innerParser.nextToken();
          if( innerParser.hasMoreTokens() ) {
            String paramValue = innerParser.nextToken();
            // Need to remove from the clear parameters any param name in the encoded params.
            // If we don't then someone could override something in the encoded param.
            map.remove( paramName );
            newUrl.addQuery( paramName, "", paramValue, true );
          } else {
            newUrl.addQuery( paramName, "", null, true );
          }
        }
      }
      context.setCurrentUrl( newUrl.build() );
      context.getParameters().resolve( "gateway.name" );
    }
    return UrlRewriteStepStatus.SUCCESS;
  }

  @Override
  public void destroy() {
  }

  private String decode( String string ) throws UnsupportedEncodingException {
    byte[] bytes = Base64.decodeBase64( string );
    EncryptionResult result = EncryptionResult.fromByteArray(bytes);
    byte[] clear = cryptoService.decryptForCluster(clusterName, 
        "encryptQueryString", 
        result.cipher, 
        result.iv, 
        result.salt);
    if (clear != null) {
      return new String(clear);
    }
    return null;
  }
}
