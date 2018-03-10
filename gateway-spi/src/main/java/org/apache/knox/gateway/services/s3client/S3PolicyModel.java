/**
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
package org.apache.knox.gateway.service.knoxs3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.knox.gateway.util.JsonUtils;

public class S3PolicyModel {
  HashMap<String, Object> policyModel = new HashMap<String, Object>();
  ArrayList<String> actionArray = new ArrayList<String>();
  HashMap<String, Object> statementMap = new HashMap<String, Object>();
  ArrayList<String> resourcesArray = new ArrayList<String>();

  public S3PolicyModel() {
    policyModel.put("Version", "2012-10-17");
    ArrayList<Map<String, Object>> statement = new ArrayList<Map<String, Object>>();
    policyModel.put("Statement", statement );
    statement.add(statementMap);
    statementMap.put("Action", actionArray );
    statementMap.put("Resource", resourcesArray);
  }

  public void setEffect(String effect) {
    statementMap.put("Effect", effect);
  }

  public void addAction(String action) {
    actionArray.add(action);
  }

  public void addResource(String resource) {
    resourcesArray.add(resource);
  }

  public void setResource(String resource) {
    statementMap.put("Resource", resource);
  }

  public String toString() {
    return JsonUtils.renderAsJsonString(policyModel);
  }
}
