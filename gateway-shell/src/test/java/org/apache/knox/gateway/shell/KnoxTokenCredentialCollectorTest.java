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
package org.apache.knox.gateway.shell;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KnoxTokenCredentialCollectorTest {

  private static final File tokenCacheBackup = new File("tokenCacheBackup.bin");

  @BeforeClass
  public static void backupTokenCache() throws Exception {
    File tokenCacheFile = getTokenCacheFile();
    if (tokenCacheFile.exists()) {
      FileUtils.copyFile(getTokenCacheFile(), tokenCacheBackup);
    }
  }

  @AfterClass
  public static void restoreTokenCache() throws Exception {
    if (tokenCacheBackup.exists()) {
      FileUtils.moveFile(tokenCacheBackup, getTokenCacheFile());
      tokenCacheBackup.delete();
    }
  }

  private static File getTokenCacheFile() throws Exception {
    return new File(System.getProperty("user.home"), getTokenCacheFileName());
  }

  private static String getTokenCacheFileName() throws Exception {
    Field f = KnoxTokenCredentialCollector.class.getDeclaredField("KNOXTOKENCACHE");
    f.setAccessible(true);
    Object fieldValue = f.get(KnoxTokenCredentialCollector.class);
    assertTrue(fieldValue instanceof String);
    return (String) fieldValue;
  }

  /**
   * KNOX-1680
   */
  @Test
  public void testEmptyTokenCache() {

    // Delete the existing token cache file, and replace it with an empty one
    try {
      File tokenCacheFile = getTokenCacheFile();
      if (tokenCacheFile.exists()) {
        tokenCacheFile.delete();
      }
      assertTrue(tokenCacheFile.createNewFile());
    } catch (Exception e) {
      // If the empty file could not be created, then this test does not make
      // any sense.
      fail("Could not create empty Knox token cache file: " + e.getMessage());
    }

    // Attempt to collect the Knox token
    KnoxTokenCredentialCollector collector = new KnoxTokenCredentialCollector();
    try {
      collector.collect();
    } catch (Exception e) {
      fail(e.getMessage());
    } finally {
      try {
        getTokenCacheFile().delete();
      } catch (Exception e) {
        // Ignore
      }
    }
  }

}
