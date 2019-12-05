/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.shell.alias;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RemoveAliasResponseTest extends AbstractResponseTest<RemoveAliasResponse> {

  @Test
  public void testRemoveAliasResponse() {
    final String CLUSTER = "testValidCluster";
    final String TEST_ALIAS = "alias1";

    HttpResponse httpResponse = createTestResponse(createTestStatusLine(HttpStatus.SC_OK),
                                                   CLUSTER,
                                                   Collections.singletonList(TEST_ALIAS));

    RemoveAliasResponse response = null;
    try {
      response = createResponse(httpResponse);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    assertEquals(CLUSTER, response.getCluster());
    assertEquals(TEST_ALIAS, response.getAlias());
  }

  @Override
  RemoveAliasResponse createResponse(HttpResponse httpResponse) {
    return new RemoveAliasResponse(httpResponse);
  }

  @Override
  StringEntity createTestEntity(String cluster, List<String> aliases) throws Exception {
    String content = "{ \"deleted\" : { \"topology\" : \"" + cluster + "\", \"alias\" : \"" + aliases.get(0) + "\" } }";
    return new StringEntity(content);
  }

}
