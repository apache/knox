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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class LocalDirectory {
  private static final GatewayMessages LOG = MessagesFactory.get(GatewayMessages.class);
  public static final Charset CHARSET = StandardCharsets.UTF_8;
  private final File base;

  public LocalDirectory(File base) {
    this.base = base;
    ensureExists(base);
  }

  private void ensureExists(File base) {
    try {
      if (!base.exists()) {
        Files.createDirectories(Paths.get(base.getAbsolutePath()));
      }
    } catch (IOException e) {
      LOG.cannotCreateLocalDirectory(base, e);
    }
  }

  public void writeFile(String name, String content) throws IOException {
    FileUtils.writeStringToFile(file(name), content, CHARSET);
  }

  public boolean deleteFile(String name) {
    return FileUtils.deleteQuietly(file(name));
  }

  public String fileContent(String name) throws IOException {
    try {
      return FileUtils.readFileToString(file(name), CHARSET);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public Set<String> list() {
    return FileUtils.listFiles(base, null, false).stream()
            .map(File::getName)
            .collect(Collectors.toSet());
  }

  private File file(String name) {
    return new File(base, name);
  }

  @Override
  public String toString() {
    return "LocalDirectory{" +
            "base='" + base + '\'' +
            '}';
  }
}
