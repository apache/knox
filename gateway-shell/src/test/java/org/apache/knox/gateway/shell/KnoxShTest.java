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
package org.apache.knox.gateway.shell;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class KnoxShTest {

  private static final String BUILD_TRUSTSTORE = "buildTrustStore";
  private static final String GATEWAY_SWITCH = "--gateway";

  @Test
  public void shouldBuildTrustStoreWithDefaultPortIfGatewayUrlHasNoPort() throws Exception {
    testBuildTruststore("https://localhost", 443);
  }

  @Test
  public void shouldBuildTrustStoreWithGatewayUrlPort() throws Exception {
    testBuildTruststore("https://localhost:8443", 8443);
  }

  private void testBuildTruststore(String gatewayUrl, int expectedPort) throws Exception {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(baos, true, "UTF-8")) {
      final KnoxSh knoxSh = new KnoxSh();
      knoxSh.out = ps;
      knoxSh.run(new String[] { BUILD_TRUSTSTORE, GATEWAY_SWITCH, gatewayUrl });
      assertTrue(new String(baos.toByteArray(), StandardCharsets.UTF_8).contains("Opening connection to localhost:" + expectedPort));
    }

  }
}
