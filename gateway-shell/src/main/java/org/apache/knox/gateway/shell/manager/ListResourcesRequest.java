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

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.KnoxSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

class ListResourcesRequest extends AbstractRequest<BasicResponse> {

  private ResourceType resourceType;

  ListResourcesRequest(KnoxSession session, ResourceType resourceType) {
    super(session);
    this.resourceType = resourceType;
  }

  public List<String> execute() throws Exception {
    return parseResourceNames( callable().call() );
  }

  @Override
  protected Callable<BasicResponse> callable() {
    return () -> {
      URIBuilder uri = uri( "/admin/api/v1/", resourceType.getName() );
      HttpGet request = new HttpGet( uri.build() );
      request.setHeader("Accept", "application/json");
      return new BasicResponse( execute( request ) );
    };
  }

  protected List<String> parseResourceNames(BasicResponse response) throws Exception {
    List<String> result = new ArrayList<>();
    JSONObject json = (JSONObject) new JSONParser(0).parse(response.getBytes());
    if (json != null) {
      JSONArray items = (JSONArray) json.get("items");
      if (items != null) {
        for (Object item1 : items) {
          JSONObject item = (JSONObject) item1;
          String name = (String) item.get("name");
          if (name != null) {
            result.add(name.substring(0, name.lastIndexOf('.')));
          }
        }
      }
    }
    return result;
  }


}
