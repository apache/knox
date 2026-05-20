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
package org.apache.knox.gateway;

import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.easymock.EasyMock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertNotNull;

public class GatewayServerTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void testRefreshGatewayConfig() throws Exception {
    GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);

    File configFile = folder.newFile("gateway-reloadable.xml");
    try (PrintWriter writer = new PrintWriter(configFile, StandardCharsets.UTF_8)) {
      writer.println("<configuration>");
      writer.println("  <property>");
      writer.println("    <name>test.prop</name>");
      writer.println("    <value>test.val</value>");
      writer.println("  </property>");
      writer.println("</configuration>");
    }

    Method refreshMethod = GatewayServer.class.getDeclaredMethod("refreshGatewayConfig", GatewayConfigImpl.class, Path.class);
    refreshMethod.setAccessible(true);

    // Initial load
    config.reloadConfiguration();
    EasyMock.expectLastCall().once();
    EasyMock.replay(config);

    refreshMethod.invoke(null, config, configFile.toPath());

    EasyMock.verify(config);

    // Check lastReloadTime is set
    Field lastReloadTimeField = GatewayServer.class.getDeclaredField("lastReloadTime");
    lastReloadTimeField.setAccessible(true);
    FileTime lastReloadTime = (FileTime) lastReloadTimeField.get(null);
    assertNotNull(lastReloadTime);

    // Second call without change should not trigger reload
    EasyMock.reset(config);
    EasyMock.replay(config);
    refreshMethod.invoke(null, config, configFile.toPath());
    EasyMock.verify(config);

    // Update file to trigger reload
    Thread.sleep(1000); // Ensure timestamp changes
    try (PrintWriter writer = new PrintWriter(configFile, StandardCharsets.UTF_8)) {
      writer.println("<configuration>");
      writer.println("  <property>");
      writer.println("    <name>test.prop</name>");
      writer.println("    <value>test.val2</value>");
      writer.println("  </property>");
      writer.println("</configuration>");
    }

    EasyMock.reset(config);
    config.reloadConfiguration();
    EasyMock.expectLastCall().once();
    EasyMock.replay(config);

    refreshMethod.invoke(null, config, configFile.toPath());
    EasyMock.verify(config);
  }
}
