/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.shell.manager;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.KnoxSession;

import java.util.concurrent.Callable;

class UndeployResourceRequest extends AbstractRequest<BasicResponse> {

  private ResourceType resourceType;
  private String resourceName;


  UndeployResourceRequest(KnoxSession session, ResourceType type, String name) {
    super(session);
    this.resourceType = type;
    this.resourceName = name;
  }

  public void execute() throws Exception {
    callable().call();
  }

  @Override
  protected Callable<BasicResponse> callable() {
    return () -> {
      URIBuilder uri = uri( "/admin/api/v1/", resourceType.getName(), "/", resourceName );
      HttpDelete request = new HttpDelete( uri.build() );
      return new BasicResponse( execute( request ) );
    };
  }

}
