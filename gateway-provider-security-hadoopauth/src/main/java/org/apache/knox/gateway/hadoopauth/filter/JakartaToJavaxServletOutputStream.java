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
package org.apache.knox.gateway.hadoopauth.filter;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * A {@code javax.servlet.ServletOutputStream} view over a {@code jakarta.servlet.ServletOutputStream}.
 * Write operations are forwarded; the non-blocking {@link WriteListener} API is not bridged
 * (Hadoop's auth handlers write synchronously, typically only error responses).
 */
class JakartaToJavaxServletOutputStream extends ServletOutputStream {

  private final jakarta.servlet.ServletOutputStream delegate;

  JakartaToJavaxServletOutputStream(jakarta.servlet.ServletOutputStream delegate) {
    this.delegate = delegate;
  }

  @Override
  public void write(int b) throws IOException {
    delegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    delegate.write(b, off, len);
  }

  @Override
  public boolean isReady() {
    return delegate.isReady();
  }

  @Override
  public void setWriteListener(WriteListener writeListener) {
    throw new UnsupportedOperationException(
        "Non-blocking ServletOutputStream is not supported by the Hadoop auth javax bridge");
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
