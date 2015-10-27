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

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class WebSSOResourceTest {
  @Test
  public void testDomainNameCreation() throws Exception {
    WebSSOResource resource = new WebSSOResource();
    // determine parent domain and wildcard the cookie domain with a dot prefix
    Assert.assertTrue(resource.getDomainName("http://www.local.com").equals(".local.com"));
    Assert.assertTrue(resource.getDomainName("http://ljm.local.com").equals(".local.com"));
    Assert.assertTrue(resource.getDomainName("http://local.home").equals(".local.home"));
    Assert.assertTrue(resource.getDomainName("http://localhost").equals(".localhost")); // chrome may not allow this
    Assert.assertTrue(resource.getDomainName("http://local.home.test.com").equals(".home.test.com"));
    
    // ip addresses can not be wildcarded - may be a completely different domain
    Assert.assertTrue(resource.getDomainName("http://127.0.0.1").equals("127.0.0.1"));
  }
}
