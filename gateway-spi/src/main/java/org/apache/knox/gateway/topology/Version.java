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
package org.apache.knox.gateway.topology;

public class Version implements Comparable<Version> {

  private int major;

  private int minor;

  private int patch;

  public Version() {
  }

  public Version(String version) {
    setVersion(version);
  }

  public Version(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  public int getMajor() {
    return major;
  }

  public void setMajor(int major) {
    this.major = major;
  }

  public int getMinor() {
    return minor;
  }

  public void setMinor(int minor) {
    this.minor = minor;
  }

  public int getPatch() {
    return patch;
  }

  public void setPatch(int patch) {
    this.patch = patch;
  }

  public void setVersion(String version) {
    if (version != null) {
      parseVersion(version);
    }
  }

  private void parseVersion(String version) {
    String[] parts = version.split("\\.");
    int length = parts.length;
    if (length >= 1) {
      major = Integer.parseInt(parts[0]);
    }
    if (length >= 2) {
      minor = Integer.parseInt(parts[1]);
    }
    if (length >= 3) {
      patch = Integer.parseInt(parts[2]);
    }
  }

  @Override
  public int compareTo(Version version) {
    if (major > version.getMajor()) {
      return 1;
    }
    if (major < version.getMajor()) {
      return -1;
    }
    if (minor > version.getMinor()) {
      return 1;
    }
    if (minor < version.getMinor()) {
      return -1;
    }
    if (patch > version.getPatch()) {
      return 1;
    }
    if (patch < version.getPatch()) {
      return -1;
    }
    return 0;
  }

  @Override
  public String toString() {
    return major +
                        "." +
                        minor +
                        "." +
                        patch;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Version)) {
      return false;
    }
    Version that = (Version) o;
    if (major == that.getMajor() && minor == that.getMinor() && patch == that.getPatch()) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }
}
