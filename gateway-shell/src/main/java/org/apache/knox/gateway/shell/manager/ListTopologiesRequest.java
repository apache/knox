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
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.Hadoop;

import java.util.ArrayList;
import java.util.List;

class ListTopologiesRequest extends ListResourcesRequest {

  ListTopologiesRequest(Hadoop session) {
    super(session, ResourceType.Topology);
  }

  @Override
  protected List<String> parseResourceNames(BasicResponse response) throws Exception {
    List<String> result = new ArrayList<>();
    JSONObject json = (JSONObject) new JSONParser(0).parse(response.getBytes());
    if (json != null) {
      JSONObject topologies = (JSONObject) json.get("topologies");
      if (topologies != null) {
        JSONArray items = (JSONArray) topologies.get("topology");
        if (items != null) {
          for (int i = 0; i < items.size(); i++) {
            JSONObject item = (JSONObject) items.get(i);
            String name = (String) item.get("name");
            if (name != null) {
              result.add(name);
            }
          }
        }
      }
    }
    return result;
  }

}
