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
package org.apache.knox.gateway.encrypturi.impl;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.encrypturi.EncryptStepContextParams;
import org.apache.knox.gateway.encrypturi.api.DecryptUriDescriptor;
import org.apache.knox.gateway.encrypturi.api.EncryptUriDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.util.urltemplate.Expander;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class DecryptUriProcessor
    implements UrlRewriteStepProcessor<DecryptUriDescriptor> {

  private String clusterName;
  private CryptoService cryptoService;
  private String param;

  @Override
  public String getType() {
    return DecryptUriDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, DecryptUriDescriptor descriptor ) throws Exception {
    clusterName = environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    GatewayServices services = environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    cryptoService = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
    param = descriptor.getParam();
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    if( param != null && !param.isEmpty() ) {
      Template template = Parser.parseTemplate( "{" + param + "}" );
      String resolvedTemplate = Expander
          .expandToString( template, context.getParameters(), context.getEvaluator() );
      String url = decode( resolvedTemplate );
      EncryptStepContextParams params = new EncryptStepContextParams();
      params.addParam( param, Collections.singletonList(url));
      context.addParameters( params );
      return UrlRewriteStepStatus.SUCCESS;
    }
    return UrlRewriteStepStatus.FAILURE;
  }

  @Override
  public void destroy() {
  }

  private String decode( String string ) throws UnsupportedEncodingException {
    byte[] bytes = Base64.decodeBase64( string );
    EncryptionResult result = EncryptionResult.fromByteArray(bytes);
    byte[] clear = cryptoService.decryptForCluster(clusterName,
        EncryptUriDescriptor.PASSWORD_ALIAS,
        result.cipher,
        result.iv,
        result.salt);
    if (clear != null) {
      return new String(clear, StandardCharsets.UTF_8);
    }
    return null;
  }
}
