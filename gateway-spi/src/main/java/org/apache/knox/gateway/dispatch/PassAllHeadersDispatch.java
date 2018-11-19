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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PassAllHeadersDispatch extends ConfigurableDispatch {

  private static final Set<String> REQUEST_EXCLUDE_HEADERS = new HashSet<>();

  static {
      REQUEST_EXCLUDE_HEADERS.add("Content-Length");
  }

  @Override
  protected void setResponseExcludeHeaders(String headers) {
    super.setResponseExcludeHeaders(String.join(",", REQUEST_EXCLUDE_HEADERS));
  }

  @Override
  protected void setRequestExcludeHeaders(String headers) {
    super.setRequestExcludeHeaders("");
  }

  @Override
  public Set<String> getOutboundResponseExcludeHeaders() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getOutboundRequestExcludeHeaders() {
    return REQUEST_EXCLUDE_HEADERS;
  }
}
