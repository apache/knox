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
package org.apache.knox.gateway.securequery;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.services.security.impl.ConfigurableEncryptor;
import org.apache.knox.gateway.util.urltemplate.Builder;
import org.apache.knox.gateway.util.urltemplate.Query;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.StringTokenizer;

public class SecureQueryDecryptProcessor implements
    UrlRewriteStepProcessor<SecureQueryDecryptDescriptor> {

  private static SecureQueryMessages log = MessagesFactory.get( SecureQueryMessages.class );
  private static final String ENCRYPTED_PARAMETER_NAME = "_";

  private ConfigurableEncryptor encryptor = null;

  @Override
  public String getType() {
    return SecureQueryDecryptDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, SecureQueryDecryptDescriptor descriptor ) throws Exception {
    encryptor = new ConfigurableEncryptor("encryptQueryString");
    encryptor.init((GatewayConfig)environment.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE));
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    //TODO: Need some way to get a reference to the keystore service and the encryption key in particular.
    Template currUrl = context.getCurrentUrl();
    Builder newUrl = new Builder( currUrl );
    Map<String,Query> map = newUrl.getQuery();
    Query query = map.remove( ENCRYPTED_PARAMETER_NAME );
    UrlRewriteStepStatus status = UrlRewriteStepStatus.FAILURE;
    status = getUrlRewriteStepStatus(context, newUrl, map, query, status);
    return status;
  }

  private UrlRewriteStepStatus getUrlRewriteStepStatus(UrlRewriteContext context, Builder newUrl, Map<String, Query> map, Query query, UrlRewriteStepStatus status) throws UnsupportedEncodingException {
    if( query != null ) {
      String value = query.getFirstValue().getPattern();
      value = decode( value );
      status = getUrlRewriteStepStatus(context, newUrl, map, status, value);
    }
    return status;
  }

  private UrlRewriteStepStatus getUrlRewriteStepStatus(UrlRewriteContext context, Builder newUrl, Map<String, Query> map, UrlRewriteStepStatus status, String value) {
    if( value != null ) {
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
       status = UrlRewriteStepStatus.SUCCESS;
    }
    return status;
  }

  @Override
  public void destroy() {
  }

  String decode( String string ) throws UnsupportedEncodingException {
    byte[] bytes = Base64.decodeBase64( string );
    EncryptionResult result = EncryptionResult.fromByteArray(bytes);
    byte[] clear = null;
    try {
      clear = encryptor.decrypt(
          result.salt, 
          result.iv, 
          result.cipher);
    } catch (Exception e) {
      log.unableToDecryptValue(e);
    }
    if (clear != null) {
      return new String(clear, "UTF-8");
    }
    return null;
  }
}
