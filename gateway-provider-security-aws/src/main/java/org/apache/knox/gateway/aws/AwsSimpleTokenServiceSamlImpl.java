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

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import java.util.List;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.knox.gateway.aws.model.AwsRolePrincipalSamlPair;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.apache.knox.gateway.aws.utils.SamlUtils;

@Slf4j
public class AwsSimpleTokenServiceSamlImpl extends BaseSamlInvokerImpl implements AwsSamlInvoker {

  private AWSSecurityTokenServiceClient stsClient = new AWSSecurityTokenServiceClient();

  @NonNull
  private FilterConfig filterConfig;


  @Override
  public void init(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AwsSamlCredentials getAwsCredentials(String samlResponse) throws AwsSamlException {
    try {
      List<AwsRolePrincipalSamlPair> roleArnPrincipalPairs = SamlUtils
          .getSamlAwsRoleAttributeValues(samlResponse);
      if (roleArnPrincipalPairs.isEmpty()) {
        throw new AwsSamlException("No AWS role found in SAML Response");
      }
      AssumeRoleWithSAMLRequest request = new AssumeRoleWithSAMLRequest()
          .withPrincipalArn(roleArnPrincipalPairs.get(0).getPrincipalArn())
          .withRoleArn(roleArnPrincipalPairs.get(0).getRoleArn())
          .withSAMLAssertion(samlResponse);
      AssumeRoleWithSAMLResult assumeRoleWithSAMLResult = stsClient.assumeRoleWithSAML(request);
      return AwsSamlCredentials.builder()
          .awsAccessKeyId(assumeRoleWithSAMLResult.getCredentials().getAccessKeyId())
          .awsSecretKey(assumeRoleWithSAMLResult.getCredentials().getSecretAccessKey())
          .sessionToken(assumeRoleWithSAMLResult.getCredentials().getSessionToken())
          .expiration(assumeRoleWithSAMLResult.getCredentials().getExpiration().getTime())
          .username(assumeRoleWithSAMLResult.getSubject())
          .build();
    } catch (ServletException | AmazonClientException e) {
      throw new AwsSamlException("Could not fetch AWS credentials using STS service", e);
    }
  }

  /**
   * {@inheritDoc}
   * <p>
   * Saves the secrets obtained from STS as a cookie in the response.
   */
  @Override
  public void processAwsCredentials(AwsSamlCredentials awsSamlCredentials,
      HttpServletRequest request, HttpServletResponse response, String domain) throws AwsSamlException {
    addAwsCookie(awsSamlCredentials, request, response, filterConfig, domain);
  }
}
