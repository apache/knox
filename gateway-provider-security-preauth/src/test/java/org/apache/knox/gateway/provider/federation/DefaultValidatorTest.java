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
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.preauth.filter.DefaultValidator;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultValidatorTest {
  @Test
  public void testDefault() throws Exception {
    final FilterConfig filterConfig = EasyMock.createMock(FilterConfig.class);
    final HttpServletRequest request = EasyMock.createMock(HttpServletRequest.class);
    DefaultValidator dv = new DefaultValidator();
    assertTrue(dv.validate(request, filterConfig));
  }

  @Test
  public void testName() {
    DefaultValidator dv = new DefaultValidator();
    assertEquals(dv.getName(), DefaultValidator.DEFAULT_VALIDATION_METHOD_VALUE);
  }
}
