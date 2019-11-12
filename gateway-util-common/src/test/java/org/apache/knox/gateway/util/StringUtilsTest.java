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
package org.apache.knox.gateway.util;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringUtilsTest {

  @Test
  public void testStartsWithIgnoreCase() throws Exception {
    assertTrue(StringUtils.startsWithIgnoreCase("This Is A Sample Text", "this is a sample"));
    assertTrue(StringUtils.startsWithIgnoreCase("THIS IS A SAMPLE TEXT", "This Is A Sample"));
    assertTrue(StringUtils.startsWithIgnoreCase("this is a sample text", "THIS IS A SAMPLE"));
  }

  @Test
  public void testEndsWithIgnoreCase() throws Exception {
    assertTrue(StringUtils.endsWithIgnoreCase("This Is A Sample Text", "a sample text"));
    assertTrue(StringUtils.endsWithIgnoreCase("THIS IS A SAMPLE TEXT", "a Sample Text"));
    assertTrue(StringUtils.endsWithIgnoreCase("this is a sample text", "A SAMPLE TEXT"));
  }
}
