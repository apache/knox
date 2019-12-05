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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ListAliasResponseTest extends AbstractResponseTest<ListAliasResponse> {

  @Test
  public void testListAliasResponse() {
    final String CLUSTER = "testValidCluster";
    final List<String> TEST_ALIASES = Arrays.asList("alias1", "alias2", "alias3");

    HttpResponse httpResponse = createTestResponse(createTestStatusLine(HttpStatus.SC_OK), CLUSTER, TEST_ALIASES);

    ListAliasResponse response = null;
    try {
      response = createResponse(httpResponse);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    assertEquals(CLUSTER, response.getCluster());
    assertEquals(TEST_ALIASES, response.getAliases());
  }

  @Override
  ListAliasResponse createResponse(HttpResponse httpResponse) {
    return new ListAliasResponse(httpResponse);
  }

  @Override
  StringEntity createTestEntity(final String cluster, final List<String> aliases) throws Exception {
    StringBuilder sb = new StringBuilder(48);
    sb.append("{ \"topology\": \"")
      .append(cluster)
      .append("\", \"aliases\" : [ ");

    Iterator<String> iter = aliases.iterator();
    while (iter.hasNext()) {
      sb.append('\"')
        .append(iter.next())
        .append('\"');
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append(" ] }");

    return new StringEntity(sb.toString());
  }

}
