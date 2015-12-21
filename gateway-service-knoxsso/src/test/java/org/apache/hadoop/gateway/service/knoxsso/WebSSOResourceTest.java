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
package org.apache.hadoop.gateway.service.knoxsso;

import org.apache.hadoop.gateway.util.RegExUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class WebSSOResourceTest {

  @Test
  public void testWhitelistMatching() throws Exception {
    String whitelist = "^https?://.*example.com:8080/.*$;" +
        "^https?://.*example.com/.*$;" +
        "^https?://.*example2.com:\\d{0,9}/.*$;" +
        "^https://.*example3.com:\\d{0,9}/.*$;" +
        "^https?://localhost:\\d{0,9}/.*$;^/.*$";

    // match on explicit hostname/domain and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example.com:8080/"));
    // match on non-required port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example.com/"));
    // match on required but any port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example2.com:1234/"));
    // fail on missing port
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example2.com/"));
    // fail on invalid port
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example.com:8081/"));
    // fail on alphanumeric port
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example.com:A080/"));
    // fail on invalid hostname/domain
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example.net:8080/"));
    // fail on required port
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example2.com/"));
    // fail on required https
    Assert.assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist, 
        "http://host.example3.com/"));
    // match on localhost and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist, 
        "http://localhost:8080/"));
    // match on local/relative path
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist, 
        "/local/resource/"));
  }
}
