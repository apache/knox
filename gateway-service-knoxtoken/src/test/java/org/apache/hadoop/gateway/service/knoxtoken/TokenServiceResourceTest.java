/**
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
package org.apache.hadoop.gateway.service.knoxtoken;

import org.apache.hadoop.gateway.service.knoxtoken.TokenResource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public class TokenServiceResourceTest {

  @Test
  public void testTokenService() throws Exception {
    Assert.assertTrue(true);
  }

  @Test
  public void testClientData() throws Exception {
    TokenResource tr = new TokenResource();

    Map<String,Object> clientDataMap = new HashMap<String,Object>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt,test=value".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 2);

    clientDataMap = new HashMap<String,Object>();
    tr.addClientDataToMap("cookie.name=hadoop-jwt".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 1);

    clientDataMap = new HashMap<String,Object>();
    tr.addClientDataToMap("".split(","), clientDataMap);
    Assert.assertTrue(clientDataMap.size() == 0);
  }
}
