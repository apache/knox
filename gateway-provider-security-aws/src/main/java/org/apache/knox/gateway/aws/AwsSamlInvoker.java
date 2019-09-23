/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.aws;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;

/**
 * Defines the interactions with AWS using a SAML Response.
 */
public interface AwsSamlInvoker {

  /**
   * Initializes the implementation.
   *
   * @param filterConfig the config used internally by {@link org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter}
   */
  void init(FilterConfig filterConfig);

  /**
   * Fetches AWS credentials using a SAML Response.
   *
   * @param samlResponse the SAML Response received from an Identity provider
   * @return {@link AwsSamlCredentials} that encapsulates credentials received from AWS
   * @throws AwsSamlException if AWS credentials cannot be fetched using the {@code samlResponse}
   */
  AwsSamlCredentials getAwsCredentials(String samlResponse) throws AwsSamlException;

  /**
   * Processes the AWS credentials obtained from SAML federation.
   *
   * @param awsSamlCredentials the AWS credentials obtained using SAML federation
   * @param request the HTTP request object
   * @param response the HTTP response object
   * @param  domain the inferred domain for the server
   */
  void processAwsCredentials(AwsSamlCredentials awsSamlCredentials, HttpServletRequest request,
      HttpServletResponse response, String domain) throws AwsSamlException;
}
