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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.knox.gateway.util;

import jakarta.activation.MimeType;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MimeTypesTest {

  @Test
  public void testCreateReturnsNullForNullBaseType() {
    assertThat(MimeTypes.create(null, StandardCharsets.UTF_8.name()), nullValue());
  }

  @Test
  public void testCreateAddsEncodingWhenCharsetIsMissing() {
    MimeType type = MimeTypes.create("application/json", StandardCharsets.UTF_8.name());

    assertThat(type.getBaseType(), is("application/json"));
    assertThat(MimeTypes.getCharset(type, null), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testCreateAddsCustomEncodingWhenCharsetIsMissing() {
    MimeType type = MimeTypes.create("text/plain", "ISO-8859-1");

    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testCreateSupportsWildcardMimeType() {
    MimeType type = MimeTypes.create("text/*", StandardCharsets.UTF_8.name());

    assertThat(type.getBaseType(), is("text/*"));
    assertThat(MimeTypes.getCharset(type, null), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testCreatePreservesExistingCharset() {
    MimeType type = MimeTypes.create("application/json; charset=ISO-8859-1", StandardCharsets.UTF_8.name());

    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testCreateReadsQuotedCharsetValue() {
    MimeType type = MimeTypes.create("text/plain; charset=\"ISO-8859-1\"", null);

    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testCreatePreservesOtherMimeTypeParameters() {
    MimeType type = MimeTypes.create("application/json; version=1", StandardCharsets.UTF_8.name());

    assertThat(type.getParameter("version"), is("1"));
    assertThat(MimeTypes.getCharset(type, null), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testCreatePreservesOtherParametersWhenCharsetAlreadyExists() {
    MimeType type = MimeTypes.create(
        "application/json; version=1; charset=ISO-8859-1", StandardCharsets.UTF_8.name());

    assertThat(type.getParameter("version"), is("1"));
    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testCreateRecognizesCharsetParameterRegardlessOfParameterNameCase() {
    MimeType type = MimeTypes.create("text/plain; CHARSET=ISO-8859-1", StandardCharsets.UTF_8.name());

    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testCreateLeavesCharsetUnsetWhenEncodingIsNull() {
    MimeType type = MimeTypes.create("application/json", null);

    assertThat(MimeTypes.getCharset(type, null), nullValue());
  }

  @Test
  public void testCreateLeavesCharsetUnsetForWildcardTypeWhenEncodingIsNull() {
    MimeType type = MimeTypes.create("*/*", null);

    assertThat(MimeTypes.getCharset(type, null), nullValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCreateRejectsMalformedMimeType() {
    MimeTypes.create("not-a-mime-type", null);
  }

  @Test
  public void testCreateIncludesOriginalMimeTypeInParseExceptionMessage() {
    try {
      MimeTypes.create("not-a-mime-type", null);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("not-a-mime-type"));
      return;
    }
    throw new AssertionError("Expected an IllegalArgumentException");
  }

  @Test
  public void testCreateRejectsEmptyMimeType() {
    try {
      MimeTypes.create("", null);
    } catch (IllegalArgumentException e) {
      return;
    }
    throw new AssertionError("Expected an IllegalArgumentException");
  }

  @Test
  public void testCreateRejectsWhitespaceOnlyMimeType() {
    try {
      MimeTypes.create("   ", null);
    } catch (IllegalArgumentException e) {
      return;
    }
    throw new AssertionError("Expected an IllegalArgumentException");
  }

  @Test
  public void testGetCharsetReturnsTypeCharsetBeforeDefault() {
    MimeType type = MimeTypes.create("text/plain; charset=ISO-8859-1", null);

    assertThat(MimeTypes.getCharset(type, StandardCharsets.UTF_8.name()), is("ISO-8859-1"));
  }

  @Test
  public void testGetCharsetReturnsDefaultWhenTypeHasNoCharset() {
    MimeType type = MimeTypes.create("text/plain", null);

    assertThat(MimeTypes.getCharset(type, StandardCharsets.UTF_8.name()), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testGetCharsetReturnsNullWhenTypeHasNoCharsetAndDefaultIsNull() {
    MimeType type = MimeTypes.create("text/plain", null);

    assertThat(MimeTypes.getCharset(type, null), nullValue());
  }

  @Test
  public void testGetCharsetReadsQuotedCharsetValue() {
    MimeType type = MimeTypes.create("text/plain; charset=\"UTF-16\"", null);

    assertThat(MimeTypes.getCharset(type, StandardCharsets.UTF_8.name()), is("UTF-16"));
  }

  @Test
  public void testGetCharsetReturnsNullForNullTypeAndDefault() {
    assertThat(MimeTypes.getCharset(null, null), nullValue());
  }

  @Test
  public void testGetCharsetReturnsDefaultForNullType() {
    assertThat(MimeTypes.getCharset(null, StandardCharsets.UTF_8.name()), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testSetCharsetReplacesExistingCharset() {
    MimeType type = MimeTypes.create("text/plain; charset=ISO-8859-1", null);

    MimeTypes.setCharset(type, StandardCharsets.UTF_8.name());

    assertThat(MimeTypes.getCharset(type, null), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testSetCharsetAddsCharsetToTypeWithoutParameters() {
    MimeType type = MimeTypes.create("text/plain", null);

    MimeTypes.setCharset(type, "ISO-8859-1");

    assertThat(MimeTypes.getCharset(type, null), is("ISO-8859-1"));
  }

  @Test
  public void testSetCharsetPreservesOtherParameters() {
    MimeType type = MimeTypes.create("text/plain; version=1", null);

    MimeTypes.setCharset(type, StandardCharsets.UTF_8.name());

    assertThat(type.getParameter("version"), is("1"));
    assertThat(MimeTypes.getCharset(type, null), is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testSetCharsetAcceptsCustomCharset() {
    MimeType type = MimeTypes.create("text/plain", null);

    MimeTypes.setCharset(type, "UTF-16");

    assertThat(MimeTypes.getCharset(type, null), is("UTF-16"));
  }

  @Test
  public void testGetDefaultCharsetForKnownMimeTypes() {
    String expectedCharset = StandardCharsets.UTF_8.name();

    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/xml"), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/json"), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/xml"), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/json"), is(expectedCharset));
  }

  @Test
  public void testGetDefaultCharsetNormalizesMimeType() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("  APPLICATION/JSON  "),
        is(StandardCharsets.UTF_8.name()));
  }

  @Test
  public void testGetDefaultCharsetNormalizesEachKnownMimeType() {
    String expectedCharset = StandardCharsets.UTF_8.name();

    assertThat(MimeTypes.getDefaultCharsetForMimeType("  TEXT/XML  "), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("  TEXT/JSON  "), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("  APPLICATION/XML  "), is(expectedCharset));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("  APPLICATION/JSON  "), is(expectedCharset));
  }

  @Test
  public void testGetDefaultCharsetReturnsNullForUnknownOrNullMimeType() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/plain"), nullValue());
    assertThat(MimeTypes.getDefaultCharsetForMimeType(null), nullValue());
  }

  @Test
  public void testGetDefaultCharsetReturnsNullForEmptyMimeType() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType(""), nullValue());
    assertThat(MimeTypes.getDefaultCharsetForMimeType("   "), nullValue());
  }

  @Test
  public void testGetDefaultCharsetDoesNotMatchMimeTypeWithParameters() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/json; charset=UTF-8"), nullValue());
  }

  @Test
  public void testGetDefaultCharsetDoesNotMatchStructuredSyntaxSuffixes() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/problem+json"), nullValue());
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/soap+xml"), nullValue());
  }
}
