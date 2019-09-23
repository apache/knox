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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.IsCloseTo.closeTo;

import java.util.Optional;
import javax.servlet.http.Cookie;
import org.apache.knox.test.mock.MockHttpServletResponse;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;

public class AwsSamlTestBase {

  protected static final String VALID_SAML_RESPONSE =
      "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
          + "xmlns:aws=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
          + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
          + "IssueInstant=\"2014-07-17T01:01:48Z\" "
          + "Destination=\"http://sp.example.com/demo1/index.php?acs\" "
          + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
          + "<aws:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
          + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
          + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
          + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
          + "  <aws:Issuer>http://idp.example.com/metadata.php</aws:Issuer>\n"
          + "    <aws:AttributeStatement>\n"
          + "      <aws:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
          + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
          + "        <aws:AttributeValue xsi:type=\"xs:string\">"
          + "arn:aws:iam::123456789012:role/role1,arn:aws:iam::123456789012:saml-provider/user1"
          + "        </aws:AttributeValue>\n"
          + "        <aws:AttributeValue xsi:type=\"xs:string\">"
          + "arn:aws:iam::123456789012:role/role2,arn:aws:iam::123456789012:saml-provider/user2"
          + "        </aws:AttributeValue>\n"
          + "      </aws:Attribute>\n"
          + "    </aws:AttributeStatement>\n"
          + "  </aws:Assertion>\n"
          + "</samlp:Response>";

  protected static final String SAML_RESPONSE_ROLE1_ARN = "arn:aws:iam::123456789012:role/role1";
  protected static final String TEST_ACCESS_KEY = "accessKey";
  protected static final String TEST_SECRET_KEY = "secretKey";
  protected static final String TEST_SESSION_TOKEN = "sessionToken";
  protected static final String TEST_USERNAME = "testusername";
  protected static final int HOUR_IN_MS = 3600 * 1000;
  protected static final int COOKIE_MAX_AGE_ERROR_MARGIN_SEC = 10;
  protected static final long TEST_COOKIE_AGE_MS = 6 * HOUR_IN_MS;

  protected Optional<Cookie> getAwsCookie(MockHttpServletResponse response) {
    return response.getCookies()
        .stream()
        .filter(cookie -> cookie.getName()
            .equals(AwsConstants.AWS_COOKIE_NAME))
        .findFirst();
  }

  protected void verifyCommonCookieParams(Cookie cookie) {
    assertThat(cookie, Is.is(IsNull.notNullValue()));
    assertThat(cookie.getSecure(), Is.is(true));
    assertThat(cookie.isHttpOnly(), Is.is(true));
    // Cookie age is in seconds
    assertThat((double) cookie.getMaxAge(), closeTo(TEST_COOKIE_AGE_MS / 1000,
        COOKIE_MAX_AGE_ERROR_MARGIN_SEC));
  }
}
