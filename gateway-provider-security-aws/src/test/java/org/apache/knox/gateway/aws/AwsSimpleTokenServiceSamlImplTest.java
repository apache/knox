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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.servlet.http.Cookie;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.apache.knox.test.mock.MockHttpServletRequest;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AwsSimpleTokenServiceSamlImplTest extends AwsSamlTestBase {

  private static final String SERVER_URL = "test.com";

  @Mock
  AWSSecurityTokenServiceClient awsSecurityTokenServiceClient;
  @InjectMocks
  AwsSimpleTokenServiceSamlImpl awsSimpleTokenServiceSaml;
  @Captor
  ArgumentCaptor<AssumeRoleWithSAMLRequest> argumentCaptor;

  @Test
  public void validate_getCredentials() throws Exception {
    String encodedAssertion = new String(Base64.encodeBase64(
        VALID_SAML_RESPONSE.getBytes("UTF-8")), "UTF-8");
    Date now = new Date();
    Credentials credentials = new Credentials().withAccessKeyId(TEST_ACCESS_KEY)
        .withSecretAccessKey(TEST_SECRET_KEY)
        .withSessionToken(TEST_SESSION_TOKEN)
        .withExpiration(now);
    AssumeRoleWithSAMLResult result = new AssumeRoleWithSAMLResult()
        .withCredentials(credentials)
        .withSubject(TEST_USERNAME);
    when(awsSecurityTokenServiceClient
        .assumeRoleWithSAML(any(AssumeRoleWithSAMLRequest.class)))
        .thenReturn(result);

    AwsSamlCredentials awsSamlCredentials = awsSimpleTokenServiceSaml
        .getAwsCredentials(encodedAssertion);

    verify(awsSecurityTokenServiceClient, times(1)).assumeRoleWithSAML(argumentCaptor.capture());
    AssumeRoleWithSAMLRequest request = argumentCaptor.getValue();
    assertThat(request.getSAMLAssertion(), is(encodedAssertion));
    assertThat(request.getRoleArn(), is(SAML_RESPONSE_ROLE1_ARN));
    assertThat(awsSamlCredentials.getAwsAccessKeyId(), is(TEST_ACCESS_KEY));
    assertThat(awsSamlCredentials.getAwsSecretKey(), is(TEST_SECRET_KEY));
    assertThat(awsSamlCredentials.getSessionToken(), is(TEST_SESSION_TOKEN));
    assertThat(awsSamlCredentials.getUsername(), is(TEST_USERNAME));
    assertThat(awsSamlCredentials.getExpiration(), is(now.getTime()));
  }

  @Test(expected = AwsSamlException.class)
  public void validate_sts_exception_throws() throws Exception {
    String encodedAssertion = new String(Base64.encodeBase64(
        VALID_SAML_RESPONSE.getBytes(StandardCharsets.UTF_8.name())), StandardCharsets.UTF_8.name());
    when(awsSecurityTokenServiceClient
        .assumeRoleWithSAML(any(AssumeRoleWithSAMLRequest.class)))
        .thenThrow(new AmazonServiceException("Internal error"));

    awsSimpleTokenServiceSaml.getAwsCredentials(encodedAssertion);
  }

  @Test
  public void valid_processCredentials() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Date now = new Date();
    AwsSamlCredentials awsSamlCredentials = AwsSamlCredentials.builder()
        .awsAccessKeyId(TEST_ACCESS_KEY)
        .awsSecretKey(TEST_SECRET_KEY)
        .sessionToken(TEST_SESSION_TOKEN)
        .expiration(now.getTime()+TEST_COOKIE_AGE_MS)
        .build();
    awsSimpleTokenServiceSaml.processAwsCredentials(awsSamlCredentials, request, response, SERVER_URL);
    Cookie cookie = getAwsCookie(response).orElse(null);
    verifyCommonCookieParams(cookie);
    assertThat(cookie.getDomain(), is(SERVER_URL));
  }
}
