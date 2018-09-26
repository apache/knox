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
package org.apache.knox.gateway.util;

import java.util.HashMap;

import org.junit.Test;


import static org.hamcrest.CoreMatchers.containsString;

public class JsonUtilsTest extends org.junit.Assert {
  String expiresIn = "\"expires_in\":\"1364487943100\"";
  String tokenType = "\"token_type\":\"Bearer\"";
  String accessToken = "\"access_token\":\"ksdfh3489tyiodhfjk\"";
  String test = '{' + expiresIn + "," + tokenType + "," + accessToken + '}';
  
  @Test
  public void testRenderAsJson() throws Exception {
    HashMap map = new HashMap();
    map.put("access_token", "ksdfh3489tyiodhfjk");
    map.put("token_type", "Bearer");
    map.put( "expires_in", "1364487943100" );
    
    String result = JsonUtils.renderAsJsonString(map);

    assertThat( result, containsString( expiresIn ) );
    assertThat( result, containsString( tokenType ) );
    assertThat( result, containsString( accessToken ) );
  }
  
  @Test
  public void testGetMapFromString() throws Exception {
    HashMap map = (HashMap) JsonUtils.getMapFromJsonString(test);
    assertEquals("ksdfh3489tyiodhfjk", map.get("access_token"));
    assertEquals("Bearer", map.get("token_type"));
    assertEquals("1364487943100", map.get("expires_in"));
  }

}
