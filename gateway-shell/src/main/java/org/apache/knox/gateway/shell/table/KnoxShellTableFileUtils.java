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
package org.apache.knox.gateway.shell.table;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class KnoxShellTableFileUtils {
  public static void persistToFile(String filePath, final String content) throws IOException {
    final Path jsonFilePath = Paths.get(filePath);
    if (!Files.exists(jsonFilePath.getParent())) {
      Files.createDirectories(jsonFilePath.getParent());
    }
    Files.deleteIfExists(jsonFilePath);
    Files.createFile(jsonFilePath);
    setPermissions(jsonFilePath);
    Files.write(jsonFilePath, content.getBytes(StandardCharsets.UTF_8));
  }

  private static void setPermissions(Path path) throws IOException {
    // clear all flags for everybody
    path.toFile().setReadable(false, false);
    path.toFile().setWritable(false, false);
    path.toFile().setExecutable(false, false);
    // allow owners to read/write
    path.toFile().setReadable(true, true);
    path.toFile().setWritable(true, true);
  }
}
