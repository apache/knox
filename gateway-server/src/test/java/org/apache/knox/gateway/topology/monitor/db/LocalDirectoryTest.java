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
    assertFalse(localDirectory.deleteFile("non_existing.txt"));
  }

  @Test
  public void testDeleteExisting() throws Exception {
    localDirectory.writeFile("new.txt", "content");
    assertTrue(localDirectory.deleteFile("new.txt"));
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