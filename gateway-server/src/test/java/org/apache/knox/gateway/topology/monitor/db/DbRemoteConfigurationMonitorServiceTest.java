/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor.db;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DbRemoteConfigurationMonitorServiceTest {
  private DbRemoteConfigurationMonitorService monitor;
  private RemoteConfigDatabase db;
  private LocalDirectory providersDir;
  private LocalDirectory descriptorsDir;
  private Instant NOW = Instant.now();

  @Before
  public void setUp() throws Exception {
    db = EasyMock.createMock(RemoteConfigDatabase.class);
    providersDir = EasyMock.createMock(LocalDirectory.class);
    descriptorsDir = EasyMock.createMock(LocalDirectory.class);
    monitor = new DbRemoteConfigurationMonitorService(db, providersDir, descriptorsDir, 60, 3600);
    monitor.start();
  }

  @Test
  public void testNothingToSynchronizeWhenDbIsEmpty() throws Exception {
    EasyMock.expect(db.selectDescriptors()).andReturn(emptyList()).anyTimes();
    EasyMock.expect(db.selectProviders()).andReturn(emptyList()).anyTimes();

    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(emptySet()).anyTimes();

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testCreatesNewProvider() throws Exception {
    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(emptySet()).anyTimes();

    EasyMock.expect(db.selectDescriptors()).andReturn(emptyList()).anyTimes();
    EasyMock.expect(db.selectProviders()).andReturn(asList(
            new RemoteConfig("test-prov", "test-prov-content", NOW))).anyTimes();

    providersDir.writeFile("test-prov", "test-prov-content");
    EasyMock.expectLastCall().once();
    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testDeletesProviderFromFileSystem() throws Exception {
    EasyMock.expect(providersDir.list()).andReturn(new HashSet<>(asList("to-be-deleted"))).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(emptySet()).anyTimes();

    EasyMock.expect(db.selectProviders()).andReturn(Arrays.asList(
            new RemoteConfig("to-be-deleted", "any", NOW, true)
    )).anyTimes();
    EasyMock.expect(db.selectDescriptors()).andReturn(emptyList()).anyTimes();

    EasyMock.expect(providersDir.deleteFile("to-be-deleted")).andReturn(true).once();

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testCreatesNewDescriptor() throws Exception {
    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(emptySet()).anyTimes();

    EasyMock.expect(db.selectProviders()).andReturn(emptyList()).anyTimes();
    EasyMock.expect(db.selectDescriptors()).andReturn(asList(
            new RemoteConfig("test-desc", "test-desc-content", NOW))).anyTimes();

    descriptorsDir.writeFile("test-desc", "test-desc-content");
    EasyMock.expectLastCall().once();
    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testDeletesDescriptorFromFileSystem() throws Exception {
    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(new HashSet<>(asList("local"))).anyTimes();

    EasyMock.expect(db.selectDescriptors()).andReturn(Arrays.asList(
            new RemoteConfig("local", "any", NOW, true))).anyTimes();
    EasyMock.expect(db.selectProviders()).andReturn(emptyList()).anyTimes();

    EasyMock.expect(descriptorsDir.deleteFile("local")).andReturn(true).once();

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testMixedDeleteUpdateAtTheSameTime() throws Exception {
    // Local FS
    EasyMock.expect(providersDir.list()).andReturn(new HashSet<>(asList("prov1"))).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(new HashSet<>(asList("desc1", "desc2", "desc3-to-be-deleted"))).anyTimes();
    // Local Contents
    EasyMock.expect(descriptorsDir.fileContent("desc1")).andReturn("desc1-same-content").anyTimes();
    EasyMock.expect(descriptorsDir.fileContent("desc2")).andReturn("desc2-local-content").anyTimes();
    EasyMock.expect(providersDir.fileContent("prov1")).andReturn(null).anyTimes();

    // Remote DB
    EasyMock.expect(db.selectProviders()).andReturn(asList(
            new RemoteConfig("prov1", "prov1-new-file", NOW)
    )).anyTimes();

    EasyMock.expect(db.selectDescriptors()).andReturn(asList(
            new RemoteConfig("desc1", "desc1-same-content", NOW),
            new RemoteConfig("desc2", "desc2-remote-content", NOW),
            new RemoteConfig("desc3-to-be-deleted", "any", NOW, true)
    )).anyTimes();

    // Expectations
    descriptorsDir.writeFile("desc2", "desc2-remote-content");
    EasyMock.expectLastCall().once();
    EasyMock.expect(descriptorsDir.deleteFile("desc3-to-be-deleted")).andReturn(true).once();

    providersDir.writeFile("prov1", "prov1-new-file");
    EasyMock.expectLastCall().once();

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testTwoSyncOnlyOneUpdate() throws Exception {
    // Local FS
    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(new HashSet<>(asList("desc1"))).anyTimes();
    // Local Contents
    EasyMock.expect(descriptorsDir.fileContent("desc1")).andReturn("local-content").anyTimes();

    // Remote DB
    EasyMock.expect(db.selectProviders()).andReturn(emptyList()).anyTimes();
    EasyMock.expect(db.selectDescriptors()).andReturn(asList(
            new RemoteConfig("desc1", "remote-content", NOW.minusSeconds(15))
    )).anyTimes();

    // Expectations
    descriptorsDir.writeFile("desc1", "remote-content");
    EasyMock.expectLastCall().once();

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @Test
  public void testTwoSyncTwoUpdate() throws Exception {
    // Local FS
    EasyMock.expect(providersDir.list()).andReturn(emptySet()).anyTimes();
    EasyMock.expect(descriptorsDir.list()).andReturn(new HashSet<>(asList("desc1"))).anyTimes();
    // Local Contents
    EasyMock.expect(descriptorsDir.fileContent("desc1")).andReturn("local-content").anyTimes();

    // Remote DB
    EasyMock.expect(db.selectProviders()).andReturn(emptyList()).anyTimes();
    EasyMock.expect(db.selectDescriptors()).andReturn(asList(
            new RemoteConfig("desc1", "remote-content", NOW.plusSeconds(10))
    )).once();
    EasyMock.expect(db.selectDescriptors()).andReturn(asList(
            new RemoteConfig("desc1", "remote-content", NOW.plusSeconds(20))
    )).once();

    // Expectations
    descriptorsDir.writeFile("desc1", "remote-content");
    EasyMock.expectLastCall().times(2);

    EasyMock.replay(providersDir, descriptorsDir, db);
    monitor.sync();
    monitor.sync();
    EasyMock.verify(providersDir, descriptorsDir, db);
  }

  @After
  public void tearDown() throws Exception {
    if (monitor != null) {
      monitor.stop();
    }
  }

}