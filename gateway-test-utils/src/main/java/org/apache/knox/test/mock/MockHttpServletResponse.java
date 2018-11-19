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
package org.apache.knox.test.mock;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MockHttpServletResponse implements HttpServletResponse {

  private Map<String, String> headers = new HashMap<>();

  @Override
  public void addCookie( Cookie cookie ) {
  }

  @Override
  public boolean containsHeader( String s ) {
    return headers.containsKey(s);
  }

  @Override
  public String encodeURL( String s ) {
    return null;
  }

  @Override
  public String encodeRedirectURL( String s ) {
    return null;
  }

  @Override
  @SuppressWarnings("deprecation")
  public String encodeUrl( String s ) {
    return null;
  }

  @Override
  public String encodeRedirectUrl( String s ) {
    return null;
  }

  @Override
  public void sendError( int i, String s ) throws IOException {
  }

  @Override
  public void sendError( int i ) throws IOException {
  }

  @Override
  public void sendRedirect( String s ) throws IOException {
  }

  @Override
  public void setDateHeader( String s, long l ) {
  }

  @Override
  public void addDateHeader( String s, long l ) {
  }

  @Override
  public void setHeader( String name, String value ) {
    headers.put(name, value);
  }

  @Override
  public void addHeader( String name, String value ) {
    headers.put(name, value);
  }

  @Override
  public void setIntHeader( String s, int i ) {
  }

  @Override
  public void addIntHeader( String s, int i ) {
  }

  @Override
  public void setStatus( int i ) {
  }

  @Override
  @SuppressWarnings("deprecation")
  public void setStatus( int i, String s ) {
  }

  @Override
  public int getStatus() {
    return 0;
  }

  @Override
  public String getHeader( String s ) {
    return headers.get(s);
  }

  @Override
  public Collection<String> getHeaders( String s ) {
    return Collections.singletonList(headers.get(s));
  }

  @Override
  public Collection<String> getHeaderNames() {
    return headers.keySet();
  }

  @Override
  public String getCharacterEncoding() {
    return null;
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public ServletOutputStream getOutputStream() throws IOException {
    return null;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    return null;
  }

  @Override
  public void setCharacterEncoding( String s ) {
  }

  @Override
  public void setContentLength( int i ) {
  }

  @Override
  public void setContentLengthLong( long l ) {
  }

  @Override
  public void setContentType( String s ) {
  }

  @Override
  public void setBufferSize( int i ) {
  }

  @Override
  public int getBufferSize() {
    return 0;
  }

  @Override
  public void flushBuffer() throws IOException {
  }

  @Override
  public void resetBuffer() {
  }

  @Override
  public boolean isCommitted() {
    return false;
  }

  @Override
  public void reset() {
  }

  @Override
  public void setLocale( Locale locale ) {
  }

  @Override
  public Locale getLocale() {
    return null;
  }
}
