package org.apache.knox.gateway.topology.monitor.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RemoteConfigDatabaseTest {
  public static final String DB_NAME = "remote_config_test";
  public static final String USER = "sa";
  public static final String PASSWORD = "";
  private RemoteConfigDatabase db;
  private JDBCDataSource dataSource;

  @Before
  public void setUp() throws Exception {
    dataSource = new JDBCDataSource();
    dataSource.setDatabaseName(DB_NAME);
    dataSource.setUser(USER);
    dataSource.setPassword(PASSWORD);
    dataSource.setUrl("jdbc:hsqldb:mem:knox;sql.syntax_pgs=true");
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

    assertEquals(1, db.selectProviders().size());
    assertEquals(1, db.selectDescriptors().size());

    db.deleteDescriptor("descriptor_1");

    assertEquals(1, db.selectProviders().size());
    assertEquals(0, db.selectDescriptors().size());

    assertEquals("provider_1", db.selectProviders().get(0).getName());
  }
}