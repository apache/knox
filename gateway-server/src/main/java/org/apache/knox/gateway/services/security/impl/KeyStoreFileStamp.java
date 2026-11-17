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
package org.apache.knox.gateway.services.security.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * The modification time, size and file key of a keystore file; a file whose stamp is unchanged is
 * considered unchanged.
 */
final class KeyStoreFileStamp {
  static final KeyStoreFileStamp MISSING = new KeyStoreFileStamp(null, -1L, null);

  private final FileTime lastModifiedTime;
  private final long size;
  private final Object fileKey;

  private KeyStoreFileStamp(FileTime lastModifiedTime, long size, Object fileKey) {
    this.lastModifiedTime = lastModifiedTime;
    this.size = size;
    this.fileKey = fileKey;
  }

  /**
   * @return the stamp of the file, {@link #MISSING} if it does not exist, or
   * <code>null</code> if its attributes cannot be read, which no cached entry matches
   */
  static KeyStoreFileStamp read(Path keyStoreFilePath) {
    try {
      // Follows symbolic links, so a replaced link target (e.g. a rotated Kubernetes secret) is a change
      final BasicFileAttributes attributes = Files.readAttributes(keyStoreFilePath, BasicFileAttributes.class);
      return new KeyStoreFileStamp(attributes.lastModifiedTime(), attributes.size(), attributes.fileKey());
    } catch (NoSuchFileException e) {
      return MISSING;
    } catch (IOException e) {
      return null;
    }
  }

  FileTime getLastModifiedTime() {
    return lastModifiedTime;
  }

  @Override
  public int hashCode() {
    return Objects.hash(lastModifiedTime, size, fileKey);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof KeyStoreFileStamp)) {
      return false;
    }
    final KeyStoreFileStamp other = (KeyStoreFileStamp) obj;
    return size == other.size
        && Objects.equals(lastModifiedTime, other.lastModifiedTime)
        && Objects.equals(fileKey, other.fileKey);
  }
}
