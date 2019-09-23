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
package org.apache.knox.gateway.aws;

import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.apache.knox.gateway.aws.utils.SamlUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

/**
 * Interceptor for AWS federation using SAML.
 */
@AllArgsConstructor
public class AwsSamlHandler {

  public static final String AWS_SAML_FEDERATION_ENABLED = "saml.aws.federation.enabled";

  private static AwsMessages log = MessagesFactory.get(AwsMessages.class);

  @NonNull
  private FilterConfig filterConfig;

  @NonNull
  private AwsSamlInvoker awsSamlInvoker;

  public AwsSamlHandler(FilterConfig filterConfig) {
    this(filterConfig, AwsSamlInvokerFactory.getAwsSamlInvoker(filterConfig));
  }

  /**
   * Processes the {@code request} and adds {@link Cookie} containing AWS credentials to {@code
   * response}.
   * <p>
   * SAML Response in {@code request} is used to get the AWS credentials. The response is unchanged
   * if there is no SAML Response in {@code request}.
   *
   * @param request the request object
   * @param response the response object
   * @param domain the domain for server
   * @throws AwsSamlException if processing the {@code request} is not successful
   */
  public void processSamlResponse(@NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response, String domain)
      throws AwsSamlException {
    String samlResponse = request.getParameter(SamlUtils.SAML_RESPONSE);
    Boolean awsFederationEnabled = Boolean.valueOf(filterConfig.getInitParameter
        (AWS_SAML_FEDERATION_ENABLED));
    if (awsFederationEnabled && samlResponse != null) {
      log.processSamlResponse();
      awsSamlInvoker.init(filterConfig);
      AwsSamlCredentials awsSamlCredentials = awsSamlInvoker.getAwsCredentials(samlResponse);
      log.processAwsCredentials();
      awsSamlInvoker.processAwsCredentials(awsSamlCredentials, request, response, domain);
      log.samlHandlerDone();
    }
  }
}
