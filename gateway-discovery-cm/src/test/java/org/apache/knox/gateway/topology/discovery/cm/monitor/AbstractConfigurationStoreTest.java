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
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.fail;

public abstract class AbstractConfigurationStoreTest {

  @Rule
  public TemporaryFolder TEMP_DIR = new TemporaryFolder();

  protected File DATA_DIR;

  @Before
  public void createDataDir() {
    try {
      DATA_DIR = TEMP_DIR.newFolder("data");
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  protected GatewayConfig createGatewayConfig() {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayDataDir()).andReturn(DATA_DIR.getAbsolutePath()).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  protected List<File> listFiles(final File dataDirectory) {
    File[] files = (new File(dataDirectory, AbstractConfigurationStore.CLUSTERS_DATA_DIR_NAME)).listFiles();
    return (files != null ? Arrays.asList(files) : Collections.emptyList());
  }

}
