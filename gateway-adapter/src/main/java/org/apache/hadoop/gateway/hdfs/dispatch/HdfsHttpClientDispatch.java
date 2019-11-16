/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.hdfs.dispatch;

import org.apache.http.HttpEntity;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * An adapter class that delegate calls to {@link org.apache.knox.gateway.hdfs.dispatch.HdfsHttpClientDispatch}
 * for backwards compatibility with package structure.
 *
 * @since 0.14.0
 * @deprecated Use {@link org.apache.knox.gateway.hdfs.dispatch.HdfsHttpClientDispatch}
 */
@Deprecated
public class HdfsHttpClientDispatch extends org.apache.knox.gateway.hdfs.dispatch.HdfsHttpClientDispatch {
  public HdfsHttpClientDispatch() throws ServletException {
    super();
  }

  /**
   * This method ensures that the request InputStream is not acquired
   * prior to a dispatch to a component such as a namenode that doesn't
   * the request body. The side effect of this is that the client does
   * not get a 100 continue from Knox which will trigger the client to
   * send the entire payload before redirect to the target component
   * like a datanode and have to send it again.
   */
  @Override
  protected HttpEntity createRequestEntity(HttpServletRequest request)
      throws IOException {
    return super.createRequestEntity(request);
  }
}
