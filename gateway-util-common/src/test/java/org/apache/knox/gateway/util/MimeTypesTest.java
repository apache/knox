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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class MimeTypesTest {

  @Test
  public void createReturnsNullForNullBase() {
    assertThat(MimeTypes.create(null, StandardCharsets.UTF_8.name()), nullValue());
  }

  @Test
  public void createBuildsMimeTypeFromBase() {
    MimeType type = MimeTypes.create("application/json", null);

    assertThat(type.getPrimaryType(), is("application"));
    assertThat(type.getSubType(), is("json"));
    assertThat(type.getParameter("charset"), nullValue());
  }

  @Test
  public void createAddsEncodingWhenBaseHasNoCharset() {
    MimeType type = MimeTypes.create("text/plain", "ISO-8859-1");

    assertThat(type.getParameter("charset"), is("ISO-8859-1"));
  }

  @Test
  public void createDoesNotAddNullEncoding() {
    MimeType type = MimeTypes.create("text/plain", null);

    assertThat(type.getParameter("charset"), nullValue());
  }

  @Test
  public void createPreservesExistingCharset() {
    MimeType type = MimeTypes.create("text/plain; charset=US-ASCII", "UTF-8");

    assertThat(type.getParameter("charset"), is("US-ASCII"));
  }

  @Test
  public void createPreservesOtherParameters() {
    MimeType type = MimeTypes.create("text/plain; format=flowed", "UTF-8");

    assertThat(type.getParameter("format"), is("flowed"));
    assertThat(type.getParameter("charset"), is("UTF-8"));
  }

  @Test
  public void createAcceptsBaseWithWhitespace() {
    MimeType type = MimeTypes.create("  application/json  ", null);

    assertThat(type.getPrimaryType(), is("application"));
    assertThat(type.getSubType(), is("json"));
  }

  @Test
  public void createWrapsMalformedMimeTypeInIllegalArgumentException() {
    try {
      MimeTypes.create("not a mime type", null);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), is("not a mime type"));
      assertThat(e.getCause(), instanceOf(Exception.class));
      assertThat(e.getCause().getMessage(), containsString("Unable to find"));
      return;
    }
    throw new AssertionError("Expected IllegalArgumentException");
  }

  @Test
  public void getCharsetReturnsExplicitCharset() {
    MimeType type = MimeTypes.create("text/plain; charset=UTF-16", null);

    assertThat(MimeTypes.getCharset(type, "UTF-8"), is("UTF-16"));
  }

  @Test
  public void getCharsetReturnsDefaultWhenTypeHasNoCharset() {
    MimeType type = MimeTypes.create("text/plain", null);

    assertThat(MimeTypes.getCharset(type, "UTF-8"), is("UTF-8"));
  }

  @Test
  public void getCharsetReturnsNullWhenTypeAndDefaultAreNull() {
    assertThat(MimeTypes.getCharset(null, null), nullValue());
    assertThat(MimeTypes.getCharset(MimeTypes.create("text/plain", null), null), nullValue());
  }

  @Test
  public void getCharsetReturnsEmptyExplicitCharset() {
    MimeType type = MimeTypes.create("text/plain; charset=\"\"", null);

    assertThat(MimeTypes.getCharset(type, "UTF-8"), equalTo(""));
  }

  @Test
  public void setCharsetAddsCharsetToType() {
    MimeType type = MimeTypes.create("application/octet-stream", null);

    MimeTypes.setCharset(type, "UTF-8");

    assertThat(type.getParameter("charset"), is("UTF-8"));
  }

  @Test
  public void setCharsetReplacesExistingCharset() {
    MimeType type = MimeTypes.create("text/plain; charset=US-ASCII", null);

    MimeTypes.setCharset(type, "UTF-8");

    assertThat(type.getParameter("charset"), is("UTF-8"));
  }

  @Test
  public void setCharsetPreservesOtherParameters() {
    MimeType type = MimeTypes.create("text/plain; format=flowed", null);

    MimeTypes.setCharset(type, "UTF-8");

    assertThat(type.getParameter("format"), is("flowed"));
    assertThat(type.getParameter("charset"), is("UTF-8"));
  }

  @Test
  public void getDefaultCharsetSupportsAllConfiguredMimeTypes() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/xml"), is("UTF-8"));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/json"), is("UTF-8"));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/xml"), is("UTF-8"));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/json"), is("UTF-8"));
  }

  @Test
  public void getDefaultCharsetTrimsAndIgnoresCase() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("  TeXt/XmL  "), is("UTF-8"));
    assertThat(MimeTypes.getDefaultCharsetForMimeType("APPLICATION/JSON"), is("UTF-8"));
  }

  @Test
  public void getDefaultCharsetReturnsNullForNull() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType(null), nullValue());
  }

  @Test
  public void getDefaultCharsetReturnsNullForUnknownMimeType() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("text/plain"), nullValue());
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/octet-stream"), nullValue());
    assertThat(MimeTypes.getDefaultCharsetForMimeType(""), nullValue());
  }

  @Test
  public void getDefaultCharsetDoesNotTreatParametersAsPartOfMimeType() {
    assertThat(MimeTypes.getDefaultCharsetForMimeType("application/json; charset=UTF-16"), nullValue());
  }
}
