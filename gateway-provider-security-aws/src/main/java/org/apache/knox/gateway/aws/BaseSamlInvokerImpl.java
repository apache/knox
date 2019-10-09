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

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.knox.gateway.aws.model.AwsSamlCredentials;
import org.apache.knox.gateway.aws.utils.CookieUtils;

public class BaseSamlInvokerImpl {

  private static final String ROOT_PATH = "/";

  protected void addAwsCookie(AwsSamlCredentials awsSamlCredentials,
      HttpServletRequest request, HttpServletResponse response,
      FilterConfig filterConfig, String domain)
      throws AwsSamlException {
    try {
      int maxAge = CookieUtils.getCookieAgeMatchingAwsCredentials(awsSamlCredentials);
      Cookie awsCookie = CookieUtils
          .createCookie(AwsConstants.AWS_COOKIE_NAME, Base64.getEncoder().encodeToString(
              awsSamlCredentials.toString().getBytes(StandardCharsets.UTF_8.name())), domain, ROOT_PATH, maxAge,
              true, true);
      response.addCookie(awsCookie);
    } catch (UnsupportedEncodingException e) {
      throw new AwsSamlException(e);
    }
  }
}
