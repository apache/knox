/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Test;

public class NoClassNameMultiLineToStringStyleTest {

  @Test
  public void shouldFormatFieldsOnSeparateLines() {
    final Object object = new Object();

    final String result = new ToStringBuilder(object, new NoClassNameMultiLineToStringStyle())
        .append("name", "knox")
        .append("port", 8443)
        .toString();

    final String expected = "name=knox" + System.lineSeparator()
        + "port=8443" + System.lineSeparator();

    assertEquals(expected, result);
  }

  @Test
  public void shouldNotIncludeClassNameOrIdentityHashCode() {
    final Object object = new Object();

    final String result = new ToStringBuilder(object, new NoClassNameMultiLineToStringStyle())
        .append("name", "knox")
        .toString();

    assertFalse(result.contains(object.getClass().getName()));
    assertFalse(result.contains(Integer.toHexString(System.identityHashCode(object))));
  }

  @Test
  public void shouldNotAddSeparatorBeforeFirstField() {
    final String result = new ToStringBuilder(new Object(), new NoClassNameMultiLineToStringStyle())
        .append("name", "knox")
        .toString();

    assertEquals("name=knox" + System.lineSeparator(), result);
  }

  @Test
  public void shouldEndWithLineSeparatorWhenNoFieldsAreAdded() {
    final String result =
        new ToStringBuilder(new Object(), new NoClassNameMultiLineToStringStyle()).toString();

    assertEquals(System.lineSeparator(), result);
  }
}
