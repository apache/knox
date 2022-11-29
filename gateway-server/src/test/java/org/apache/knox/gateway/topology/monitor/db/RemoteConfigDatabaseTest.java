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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class RemoteConfigDatabaseTest {
  public static final String DB_NAME = "remote_config_test";
  public static final String USER = "sa";
  public static final String PASSWORD = "";
  private static JDBCDataSource dataSource;
  private RemoteConfigDatabase db;

  @BeforeClass
  public static void setUpClass() throws Exception {
    dataSource = new JDBCDataSource();
    dataSource.setDatabaseName(DB_NAME);
    dataSource.setUser(USER);
    dataSource.setPassword(PASSWORD);
    dataSource.setUrl("jdbc:hsqldb:mem:knox;sql.syntax_pgs=true"); // sql.syntax_pgs => use postgres syntax
  }

  @Before
  public void setUp() throws Exception {
    db = new RemoteConfigDatabase(dataSource);
  }

  @After
  public void tearDown() throws Exception {
    try (Connection connection = dataSource.getConnection(USER, PASSWORD);
         Statement statement = connection.createStatement()) {
         statement.execute("DROP TABLE KNOX_PROVIDERS");
         statement.execute("DROP TABLE KNOX_DESCRIPTORS");
    }
  }

  @AfterClass
  public static void tearDownClass() throws Exception {
    try (Connection connection = dataSource.getConnection(USER, PASSWORD);
         Statement statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    }
  }

  @Test
  public void testEmpty() {
    assertTrue(db.selectDescriptors().isEmpty());
    assertTrue(db.selectDescriptors().isEmpty());
  }

  @Test
  public void testList() {
    db.putProvider("provider_1", "test provider1 content");
    db.putProvider("provider_2", "test provider2 content");
    db.putDescriptor("descriptor_1", "test descriptor content");

    List<RemoteConfig> providers = db.selectProviders();
    List<RemoteConfig> descriptors = db.selectDescriptors();

    assertEquals(2, providers.size());
    assertEquals(1, descriptors.size());

    RemoteConfig descriptor = descriptors.get(0);
    assertEquals("descriptor_1", descriptor.getName());
    assertEquals("test descriptor content", descriptor.getContent());
    assertTrue(Duration.between(Instant.now(), descriptor.getLastModified()).toMillis() < 1000);

    RemoteConfig provider1 = providers.stream().filter(each -> each.getName().equals("provider_1")).findFirst().get();
    assertEquals("test provider1 content", provider1.getContent());
    assertTrue(Duration.between(Instant.now(), provider1.getLastModified()).toMillis() < 1000);

    RemoteConfig provider2 = providers.stream().filter(each -> each.getName().equals("provider_2")).findFirst().get();
    assertEquals("test provider2 content", provider2.getContent());
    assertTrue(Duration.between(Instant.now(), provider2.getLastModified()).toMillis() < 1000);
  }

  @Test
  public void testInsertOverwrite() {
    db.putProvider("provider_1", "test provider1 content v1");
    db.putProvider("provider_1", "test provider1 content v2");
    List<RemoteConfig> providers = db.selectProviders();
    assertEquals(1, providers.size());
    assertEquals("test provider1 content v2", db.selectProviders().get(0).getContent());

    db.putDescriptor("descriptor_1", "test descriptor1 content v1");
    db.putDescriptor("descriptor_1", "test descriptor1 content v2");
    List<RemoteConfig> descriptors = db.selectDescriptors();
    assertEquals(1, descriptors.size());
    assertEquals("test descriptor1 content v2", db.selectDescriptors().get(0).getContent());
  }

  @Test
  public void testDelete() {
    db.putProvider("provider_1", "test provider1 content");
    db.putProvider("provider_2", "test provider2 content");
    db.putDescriptor("descriptor_1", "test descriptor content");

    assertEquals(2, db.selectProviders().size());
    assertEquals(1, db.selectDescriptors().size());

    db.deleteProvider("provider_2");

    assertEquals(2, db.selectProviders().size());
    for (RemoteConfig provider : db.selectProviders()) {
      if (provider.getName().equals("provider_1")) {
        assertFalse(provider.isDeleted());
      } else if (provider.getName().equals("provider_2")) {
        assertTrue(provider.isDeleted());
      } else {
        fail("unexpected name: " + provider.getName());
      }
    }

    db.deleteDescriptor("descriptor_1");

    assertEquals(2, db.selectProviders().size());
    assertEquals(1, db.selectDescriptors().size());

    assertTrue(db.selectDescriptors().get(0).isDeleted());
  }

  @Test
  public void testPutBackDeletedOne() {
    db.putProvider("provider_1", "test provider1 content");
    db.putDescriptor("descriptor_1", "test descriptor content");

    db.deleteProvider("provider_1");
    db.deleteDescriptor("descriptor_1");

    assertEquals(1, db.selectProviders().size());
    assertTrue(db.selectProviders().get(0).isDeleted());
    assertEquals(1, db.selectDescriptors().size());
    assertTrue(db.selectDescriptors().get(0).isDeleted());

    db.putProvider("provider_1", "test provider1 new content");
    db.putDescriptor("descriptor_1", "test descriptor new content");

    assertEquals(1, db.selectProviders().size());
    assertEquals(1, db.selectDescriptors().size());

    assertEquals(1, db.selectProviders().size());
    assertFalse(db.selectProviders().get(0).isDeleted());
    assertEquals(1, db.selectDescriptors().size());
    assertFalse(db.selectDescriptors().get(0).isDeleted());

    assertEquals("test provider1 new content", db.selectProviders().get(0).getContent());
    assertEquals("test descriptor new content", db.selectDescriptors().get(0).getContent());
  }

  @Test
  public void testPhysicalDelete() {
    db.putProvider("provider_1", "any");
    db.putDescriptor("descriptor_1", "any");

    assertEquals(0, db.cleanTables(0));

    assertEquals(1, db.selectProviders().size());
    assertEquals(1, db.selectDescriptors().size());

    db.deleteDescriptor("descriptor_1");
    db.deleteProvider("provider_1");

    assertEquals(2, db.cleanTables(0));

    assertEquals(0, db.selectProviders().size());
    assertEquals(0, db.selectDescriptors().size());
  }
}