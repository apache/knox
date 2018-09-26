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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VersionTest {

  @Test
  public void testDefaultVersion() {
    Version version = new Version();
    assertEquals(0, version.getMajor());
    assertEquals(0, version.getMinor());
    assertEquals(0, version.getPatch());
    assertEquals("0.0.0", version.toString());
  }

  @Test
  public void testVersion() {
    Version version = new Version("1.2.3");
    assertEquals(1, version.getMajor());
    assertEquals(2, version.getMinor());
    assertEquals(3, version.getPatch());
    assertEquals("1.2.3", version.toString());
    version = new Version(4, 5, 6);
    assertEquals(4, version.getMajor());
    assertEquals(5, version.getMinor());
    assertEquals(6, version.getPatch());
    assertEquals("4.5.6", version.toString());
    Version other = new Version("4.5.6");
    assertTrue(version.equals(other));
  }
}
