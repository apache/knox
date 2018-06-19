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

import org.apache.knox.gateway.service.idbroker.aws.AWSPolicyModel;
import org.apache.knox.gateway.util.JsonUtils;
import org.junit.Test;

public class S3BucketsResourceTest {
  @Test
  public void testPolicyCreation() {

    String policy = "{\n" +
    "  \"Version\": \"2012-10-17\",\n" +
    "  \"Statement\": [\n" +
    "    {\n" +
    "      \"Effect\": \"Allow\",\n" +
    "      \"Action\": [\n" +
    "        \"s3:Get*\",\n" +
    "        \"s3:List*\"\n" +
    // "        \"s3:Delete*\"\n" +
    "      ],\n" +
    "      \"Resource\": \"*\"\n" +
    "    }\n" +
    "  ]\n" +
    "}";
    System.out.println(policy);

    HashMap<String, Object> policyModel = new HashMap<String, Object>();
    policyModel.put("Version", "2012-10-17");
    ArrayList<Map<String, Object>> statement = new ArrayList<Map<String, Object>>();

    policyModel.put("Version", "2012-10-17");
    policyModel.put("Statement", statement );
    HashMap<String, Object> statementMap = new HashMap<String, Object>();
    statementMap.put("Effect", "Allow");
    ArrayList<String> actionArray = new ArrayList<String>();
    actionArray.add("s3:Get*");
    actionArray.add("s3:List*");
    statementMap.put("Action", actionArray );
    statement.add(statementMap);
    policyModel.put("Resource", "*");
    
    System.out.println(JsonUtils.renderAsJsonString(policyModel));
    
    AWSPolicyModel model = new AWSPolicyModel();
    model.setEffect("Allow");
    model.addAction("s3:Get*");
    model.addAction("s3:List*");
    model.setResource("*");
    System.out.println(model);

    model = new AWSPolicyModel();
    model.setEffect("Allow");
    model.addAction("s3:Get*");
    model.addAction("s3:List*");
    model.addResource("this");
    model.addResource("that");
    System.out.println(model);
}
}