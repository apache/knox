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
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

public class SecureQueryEncryptProcessor
    implements UrlRewriteStepProcessor<SecureQueryEncryptDescriptor> {

  private static SecureQueryMessages log = MessagesFactory.get( SecureQueryMessages.class );
  private static final String ENCRYPTED_PARAMETER_NAME = "_";

  private ConfigurableEncryptor encryptor;

  @Override
  public String getType() {
    return SecureQueryEncryptDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, SecureQueryEncryptDescriptor descriptor ) throws Exception {
    encryptor = new ConfigurableEncryptor("encryptQueryString");
    encryptor.init(environment.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE));
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

  private String encode( String string ) throws Exception {
    try {
      EncryptionResult result = encryptor.encrypt(string);
      return Base64.encodeBase64URLSafeString(result.toByteAray());
    } catch (Exception e) {
      log.unableToEncryptValue(e);
      throw e;
    }
  }
}
