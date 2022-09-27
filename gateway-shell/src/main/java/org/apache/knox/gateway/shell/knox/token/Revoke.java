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
package org.apache.knox.gateway.shell.knox.token;

import java.nio.charset.StandardCharsets;

import org.apache.http.HttpRequest;
import org.apache.http.entity.StringEntity;
import org.apache.knox.gateway.shell.HttpDelete;
import org.apache.knox.gateway.shell.KnoxSession;

public class Revoke {

  public static class Request extends AbstractTokenLifecycleRequest {

    public static final String OPERATION = "revoke";
    private HttpDelete deleteRequest;

    Request(final KnoxSession session, final String token) {
      this(session, token, null);
    }

    Request(final KnoxSession session, final String token, final String doAsUser) {
      super(session, token, doAsUser);
      initDeleteRequest(token);
    }

    private void initDeleteRequest(String token) {
      this.deleteRequest = new HttpDelete(getRequestURI());
      this.deleteRequest.setEntity(new StringEntity(token, StandardCharsets.UTF_8));
    }

    @Override
    protected String getOperation() {
      return OPERATION;
    }

    @Override
    protected HttpRequest getRequest() {
      return this.deleteRequest;
    }
  }

}
