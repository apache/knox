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

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

/**
 * A {@code javax.servlet.ServletInputStream} view over a {@code jakarta.servlet.ServletInputStream}.
 * Read operations are forwarded; the non-blocking {@link ReadListener} API is not bridged
 * (Hadoop's auth handlers read synchronously).
 */
class JakartaToJavaxServletInputStream extends ServletInputStream {

  private final jakarta.servlet.ServletInputStream delegate;

  JakartaToJavaxServletInputStream(jakarta.servlet.ServletInputStream delegate) {
    this.delegate = delegate;
  }

  @Override
  public int read() throws IOException {
    return delegate.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return delegate.read(b, off, len);
  }

  @Override
  public boolean isFinished() {
    return delegate.isFinished();
  }

  @Override
  public boolean isReady() {
    return delegate.isReady();
  }

  @Override
  public void setReadListener(ReadListener readListener) {
    throw new UnsupportedOperationException(
        "Non-blocking ServletInputStream is not supported by the Hadoop auth javax bridge");
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }
}
