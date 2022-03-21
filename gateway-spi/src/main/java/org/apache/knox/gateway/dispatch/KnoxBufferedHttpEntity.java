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
package org.apache.knox.gateway.dispatch;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.util.Args;

/**
 * A {@link BufferedHttpEntity} implementation with the following differences:
 * <ul>
 * <li>Reading the content of the given entity does not happen at instance creation time (i.e not in the constructor)
 * <li>Content length calculation is not based on the buffer but a delegate to the wrapped entity
 * </ul>
 */
public class KnoxBufferedHttpEntity extends HttpEntityWrapper {

  private byte[] buffer;

  public KnoxBufferedHttpEntity(final HttpEntity entity) throws IOException {
    super(entity);
    // this time we skip reading the entity content to buffer
  }

  @Override
  public long getContentLength() {
    return wrappedEntity.getContentLength();
  }

  @Override
  public InputStream getContent() throws IOException {
    readBuffer();
    return this.buffer != null ? new ByteArrayInputStream(this.buffer) : super.getContent();
  }

  private boolean shouldPopulateBuffer() {
    return !wrappedEntity.isRepeatable() || wrappedEntity.getContentLength() < 0;
  }

  private void readBuffer() throws IOException {
    if (this.buffer == null && shouldPopulateBuffer()) {
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      wrappedEntity.writeTo(out);
      out.flush();
      this.buffer = out.toByteArray();
    }
  }

  @Override
  public boolean isChunked() {
    return shouldPopulateBuffer() && super.isChunked();
  }

  /**
   * Tells that this entity is repeatable.
   *
   * @return {@code true}
   */
  @Override
  public boolean isRepeatable() {
    return true;
  }

  @Override
  public void writeTo(final OutputStream outStream) throws IOException {
    Args.notNull(outStream, "Output stream");
    readBuffer();
    if (this.buffer != null) {
      outStream.write(this.buffer);
    } else {
      super.writeTo(outStream);
    }
  }

  @Override
  public boolean isStreaming() {
    return shouldPopulateBuffer() && super.isStreaming();
  }

}
