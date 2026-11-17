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

import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.security.MessageDigest;

/**
 * A loaded keystore, with the file stamp, store type and password digest it was loaded with.
 */
final class CachedKeyStore {
  /*
   * A file changed twice within its file system's timestamp granularity may keep the same stamp, so a
   * store loaded within this window of its last change is checked again once the window has passed.
   */
  private static final long FILE_TIMESTAMP_GRANULARITY_MILLIS = 2000L;

  private final String storeType;
  private final byte[] passwordDigest;
  private final KeyStoreFileStamp stamp;
  private final KeyStore keyStore;
  private final long validUntil;

  CachedKeyStore(String storeType, byte[] passwordDigest, KeyStoreFileStamp stamp,
                 KeyStore keyStore, long loadStartedAt) {
    this.storeType = storeType;
    this.passwordDigest = passwordDigest;
    this.stamp = stamp;
    this.keyStore = keyStore;
    // A change within the timestamp granularity of the last one may not show in the stamp
    final FileTime lastModifiedTime = stamp == null ? null : stamp.getLastModifiedTime();
    if (lastModifiedTime != null && lastModifiedTime.toMillis() > loadStartedAt - FILE_TIMESTAMP_GRANULARITY_MILLIS) {
      this.validUntil = lastModifiedTime.toMillis() + FILE_TIMESTAMP_GRANULARITY_MILLIS;
    } else {
      this.validUntil = Long.MAX_VALUE;
    }
  }

  KeyStore getKeyStore() {
    return keyStore;
  }

  boolean isCurrent(String storeType, byte[] passwordDigest, KeyStoreFileStamp currentStamp) {
    return currentStamp != null
        && currentStamp.equals(stamp)
        && this.storeType.equalsIgnoreCase(storeType)
        && MessageDigest.isEqual(this.passwordDigest, passwordDigest)
        && System.currentTimeMillis() < validUntil;
  }
}
