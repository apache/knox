/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.junit.Test;

/**
 * Verifies the authorization-code success redirect stays well-formed when the registered
 * redirect_uri already carries a query string (review finding M7). Appending {@code ?code=...}
 * unconditionally produced a second {@code ?}, so the client parsed neither code nor state.
 */
public class AuthorizeResourceSuccessRedirectTest {

  private static List<NameValuePair> queryParams(final String location) {
    return URLEncodedUtils.parse(URI.create(location), java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String param(final List<NameValuePair> params, final String name) {
    return params.stream().filter(p -> p.getName().equals(name)).map(NameValuePair::getValue)
        .findFirst().orElse(null);
  }

  @Test
  public void testRedirectUriWithoutQueryUsesQuestionMark() throws Exception {
    final String location = AuthorizeResource.buildSuccessRedirect(
        "https://app.example/cb", "the code", "the state");
    assertTrue("A redirect_uri without a query must start its params with '?'.",
        location.startsWith("https://app.example/cb?"));
    final List<NameValuePair> params = queryParams(location);
    assertEquals("the code", param(params, "code"));
    assertEquals("the state", param(params, "state"));
  }

  @Test
  public void testRedirectUriWithExistingQueryUsesAmpersand() throws Exception {
    final String location = AuthorizeResource.buildSuccessRedirect(
        "https://app.example/cb?ui=dark", "the code", "the state");
    // Exactly one '?' -- the code/state must be appended with '&', not a second '?'.
    assertEquals("There must be exactly one query separator.", 1, location.chars().filter(c -> c == '?').count());
    final List<NameValuePair> params = queryParams(location);
    assertEquals("The pre-existing query param must survive.", "dark", param(params, "ui"));
    assertEquals("the code", param(params, "code"));
    assertEquals("the state", param(params, "state"));
  }
}
