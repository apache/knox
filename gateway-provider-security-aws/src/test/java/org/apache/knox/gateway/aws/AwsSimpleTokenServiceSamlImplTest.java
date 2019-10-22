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

import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleWithSAMLResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import javax.servlet.http.Cookie;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.apache.knox.test.mock.MockHttpServletRequest;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

public class AwsSimpleTokenServiceSamlImplTest extends AwsSamlTestBase {

  private static final String SERVER_URL = "test.com";

  @Test
  public void validate_getCredentials() throws Exception {
    AWSSecurityTokenServiceClient stsClient = EasyMock.createNiceMock(AWSSecurityTokenServiceClient.class);
    AwsSimpleTokenServiceSamlImpl awsSimpleTokenServiceSaml = new AwsSimpleTokenServiceSamlImpl(stsClient);

    Capture<AssumeRoleWithSAMLRequest> captureArgument = newCapture();
    String encodedAssertion = new String(Base64.getEncoder().encode(
            VALID_SAML_RESPONSE.getBytes(StandardCharsets.UTF_8.name())), StandardCharsets.UTF_8.name());
    Date now = new Date();
    Credentials credentials = new Credentials().withAccessKeyId(TEST_ACCESS_KEY)
        .withSecretAccessKey(TEST_SECRET_KEY)
        .withSessionToken(TEST_SESSION_TOKEN)
        .withExpiration(now);
    AssumeRoleWithSAMLResult result = new AssumeRoleWithSAMLResult()
        .withCredentials(credentials)
        .withSubject(TEST_USERNAME);
    expect(stsClient
        .assumeRoleWithSAML(capture(captureArgument)))
        .andReturn(result).once();
    replay(stsClient);

    AwsSamlCredentials awsSamlCredentials = awsSimpleTokenServiceSaml
        .getAwsCredentials(encodedAssertion);

    verify(stsClient);

    AssumeRoleWithSAMLRequest request = captureArgument.getValue();
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
    AWSSecurityTokenServiceClient stsClient = EasyMock.createNiceMock(AWSSecurityTokenServiceClient.class);
    AwsSimpleTokenServiceSamlImpl awsSimpleTokenServiceSaml = new AwsSimpleTokenServiceSamlImpl(stsClient);

    String encodedAssertion = new String(Base64.getEncoder().encode(
        VALID_SAML_RESPONSE.getBytes(StandardCharsets.UTF_8.name())), StandardCharsets.UTF_8.name());
    expect(stsClient
            .assumeRoleWithSAML(isA((AssumeRoleWithSAMLRequest.class))))
        .andThrow(new AmazonServiceException("Internal error"));
    replay(stsClient);
    awsSimpleTokenServiceSaml.getAwsCredentials(encodedAssertion);
  }

  @Test
  public void valid_processCredentials() throws Exception {
    AWSSecurityTokenServiceClient stsClient = EasyMock.createNiceMock(AWSSecurityTokenServiceClient.class);
    AwsSimpleTokenServiceSamlImpl awsSimpleTokenServiceSaml = new AwsSimpleTokenServiceSamlImpl(stsClient);

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    Date now = new Date();
    AwsSamlCredentials awsSamlCredentials =  new AwsSamlCredentials(
        TEST_ACCESS_KEY,
        TEST_SECRET_KEY,
        TEST_SESSION_TOKEN,
        (now.getTime()+TEST_COOKIE_AGE_MS),
        null);
    awsSimpleTokenServiceSaml.processAwsCredentials(awsSamlCredentials, request, response, SERVER_URL);
    Cookie cookie = getAwsCookie(response).orElse(null);
    verifyCommonCookieParams(cookie);
    assertThat(cookie.getDomain(), is(SERVER_URL));
  }
}
