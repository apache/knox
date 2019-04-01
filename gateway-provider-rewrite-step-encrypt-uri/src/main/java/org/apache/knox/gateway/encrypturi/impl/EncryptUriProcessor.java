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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.encrypturi.EncryptStepContextParams;
import org.apache.knox.gateway.encrypturi.api.EncryptUriDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.util.urltemplate.Expander;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

public class EncryptUriProcessor
    implements UrlRewriteStepProcessor<EncryptUriDescriptor> {

  private String clusterName;
  private CryptoService cryptoService;
  private String template;
  private String param;

  @Override
  public String getType() {
    return EncryptUriDescriptor.STEP_NAME;
  }

  @Override
  public void initialize( UrlRewriteEnvironment environment, EncryptUriDescriptor descriptor ) throws Exception {
    clusterName = environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE );
    GatewayServices services = environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    cryptoService = services.getService(ServiceType.CRYPTO_SERVICE);
    template = descriptor.getTemplate();
    param = descriptor.getParam();
  }

  @Override
  public UrlRewriteStepStatus process( UrlRewriteContext context ) throws Exception {
    if( param != null && !param.isEmpty() && template != null && !template.isEmpty() ) {
      Template uri = Parser.parseTemplate( template );
      String resolvedTemplate = Expander
          .expandToString( uri, context.getParameters(), context.getEvaluator() );
      if( resolvedTemplate != null && !resolvedTemplate.isEmpty() ) {
        String endcoedUrl = encode( resolvedTemplate );
        EncryptStepContextParams params = new EncryptStepContextParams();
        params.addParam( param, Collections.singletonList(endcoedUrl));
        context.addParameters( params );
        return UrlRewriteStepStatus.SUCCESS;
      }
    }
    return UrlRewriteStepStatus.FAILURE;
  }

  @Override
  public void destroy() {
  }

  private String encode( String string ) throws UnsupportedEncodingException {
    EncryptionResult result = cryptoService.encryptForCluster(clusterName,
        EncryptUriDescriptor.PASSWORD_ALIAS,
        string.getBytes(StandardCharsets.UTF_8));
    string = Base64.encodeBase64URLSafeString(result.toByteAray());
    return string;
  }

}
