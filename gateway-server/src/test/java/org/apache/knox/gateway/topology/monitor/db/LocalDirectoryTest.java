package org.apache.knox.gateway.topology.monitor.db;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalDirectoryTest {
  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();
  private LocalDirectory localDirectory;

  @Before
  public void setUp() throws Exception {
    localDirectory = new LocalDirectory(testFolder.getRoot());
  }

  @After
  public void tearDown() throws Exception {
    if (localDirectory != null) {
      localDirectory.list().forEach(localDirectory::deleteFile);
    }
  }

  @Test
  public void testEmpty() {
    assertEquals(0, localDirectory.list().size());
  }

  @Test
  public void testDeleteNonExisting() {
    assertEquals(false, localDirectory.deleteFile("non_existing.txt"));
  }

  @Test
  public void testDeleteExisting() throws Exception {
    localDirectory.writeFile("new.txt", "content");
    assertEquals(true, localDirectory.deleteFile("new.txt"));
  }

  @Test
  public void testCreateNewWithContent() throws Exception {
    localDirectory.writeFile("new.txt", "content");
    assertEquals("content", localDirectory.fileContent("new.txt"));
  }

  @Test
  public void testOverwriteExisting() throws Exception {
    localDirectory.writeFile("existing.txt", "old content");
    localDirectory.writeFile("existing.txt", "new content");
    assertEquals("new content", localDirectory.fileContent("existing.txt"));
  }

  @Test
  public void testContentOfNonExistingIsNull() throws Exception {
    assertEquals(null, localDirectory.fileContent("non_existing"));
  }

  @Test
  public void testList() throws Exception {
    localDirectory.writeFile("1.txt", "any");
    localDirectory.writeFile("2.txt", "any");
    assertEquals(2, localDirectory.list().size());
    assertTrue(localDirectory.list().contains("1.txt"));
    assertTrue(localDirectory.list().contains("2.txt"));
  }

  @Test
  public void testTwoWithDifferentContent() throws Exception {
    localDirectory.writeFile("1.txt", "content 1");
    localDirectory.writeFile("2.txt", "content 2");
    assertEquals("content 1", localDirectory.fileContent("1.txt"));
    assertEquals("content 2", localDirectory.fileContent("2.txt"));
  }

  @Test
  public void testCreatesDirectoryIfDoesNotExist() throws Exception {
    LocalDirectory subDir = new LocalDirectory(new File(testFolder.getRoot(), "sub1/sub2"));
    assertTrue(subDir.list().isEmpty());
    subDir.writeFile("s.txt", "any");
    assertTrue(subDir.list().contains("s.txt"));
  }
}