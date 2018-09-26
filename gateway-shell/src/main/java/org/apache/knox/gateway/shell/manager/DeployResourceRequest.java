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


import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.Hadoop;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.concurrent.Callable;

class DeployResourceRequest extends AbstractRequest<BasicResponse> {

  private ResourceType resourceType     = null;
  private String       resourceName     = null;
  private String       resourceFileName = null;


  DeployResourceRequest(Hadoop session, ResourceType type, String name, String resourceFileName) {
    super(session);
    this.resourceType     = type;
    this.resourceName     = name;
    this.resourceFileName = resourceFileName;
  }

  public void execute() throws Exception {
    if (isExistingResource()) {
      throw new IllegalStateException("A " + resourceType.getName() + " resource with the same name (" + resourceName + ") is already deployed.");
    } else {
      callable().call();
    }
  }

  @Override
  protected Callable<BasicResponse> callable() {
    return () -> {
      URIBuilder uri = uri( "/admin/api/v1/", resourceType.getName(), "/", resourceName );
      HttpPut request = new HttpPut( uri.build() );

      if (resourceFileName != null) {
        File resource = new File(resourceFileName);
        if (!resource.exists()) {
          throw new FileNotFoundException(resourceFileName);
        }
        HttpEntity entity = new FileEntity(new File(resourceFileName), ContentType.APPLICATION_JSON);
        request.setEntity(entity);
      }
      return new BasicResponse( execute( request ) );
    };
  }

  private boolean isExistingResource() throws Exception {
    boolean result = false;
    List<String> existing = (new ListResourcesRequest(hadoop(), resourceType)).execute();
    if (existing != null) {
      result = existing.contains(resourceName);
    }
    return result;
  }

}
