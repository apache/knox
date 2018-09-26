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
package org.apache.knox.gateway.util.urltemplate;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TokenTest {

  @Test
  public void testConstructorAndGetters() throws Exception {

    Token token = new Token( "test-parameter-name", "test-original-value", "test-effective-value", true );

    assertThat( token.parameterName, is( "test-parameter-name" ) );
    assertThat( token.originalPattern, is( "test-original-value" ) );
    assertThat( token.effectivePattern, is( "test-effective-value" ) );

    assertThat( token.getParameterName(), is( "test-parameter-name" ) );
    assertThat( token.getOriginalPattern(), is( "test-original-value" ) );
    assertThat( token.getEffectivePattern(), is( "test-effective-value" ) );

  }

}
