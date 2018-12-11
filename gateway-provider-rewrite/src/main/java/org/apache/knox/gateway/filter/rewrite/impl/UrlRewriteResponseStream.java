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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.filter.GatewayResponse;
import org.apache.knox.gateway.servlet.SynchronousServletOutputStreamAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

//TODO: This needs to be coded much more efficiently!
public class UrlRewriteResponseStream extends
    SynchronousServletOutputStreamAdapter {

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private GatewayResponse response;
  private ByteArrayOutputStream buffer;

  public UrlRewriteResponseStream( GatewayResponse response ) {
    this.response = response;
    this.buffer = new ByteArrayOutputStream( DEFAULT_BUFFER_SIZE );
  }

  @Override
  public void write( int b ) {
    buffer.write( b );
  }

  @Override
  public void close() throws IOException {
    try(InputStream stream = new ByteArrayInputStream(buffer.toByteArray())) {
      response.streamResponse(stream);
    }
  }
}
