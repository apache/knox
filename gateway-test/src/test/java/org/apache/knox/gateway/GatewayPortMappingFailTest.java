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
package org.apache.knox.gateway;

import org.apache.http.HttpStatus;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.ReleaseTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import static io.restassured.RestAssured.given;
import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;

/**
 * Test the fail cases for the Port Mapping Feature
 */
@Category(ReleaseTest.class)
public class GatewayPortMappingFailTest extends PortMappingHelper {

  /**
   * Create an instance
   */
  public GatewayPortMappingFailTest() {
    super();
  }

  @BeforeClass
  public static void setup() throws Exception {
    eeriePort = getAvailablePort(1240, 49151);
    ConcurrentHashMap<String, Integer> topologyPortMapping = new ConcurrentHashMap<>();
    topologyPortMapping.put("eerie", eeriePort);
    init(null, topologyPortMapping);
  }

  @AfterClass
  public static void cleanup() throws Exception {
    LOG_ENTER();
    driver.cleanup();
    driver.reset();
    masterServer.reset();
    LOG_EXIT();
  }


  /*
   * Fail when trying to use this feature on the standard port.
   * Here we do not have Default Topology Feature not enabled.
   *
   * http://localhost:{gatewayPort}/webhdfs/v1
   */
  @Test(timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testMultiPortOperationFail() throws IOException {
    LOG_ENTER();
    final String url = "http://localhost:" + driver.getGatewayPort() + "/webhdfs" ;

    String password = "hdfs-password";
    String username = "hdfs";

    masterServer.expect()
        .method("GET")
        .pathInfo("/webhdfs/v1/")
        .queryParam("op", "LISTSTATUS")
        .queryParam("user.name", username)
        .respond()
        .status(HttpStatus.SC_OK)
        .content(driver.getResourceBytes("webhdfs-liststatus-success.json"))
        .contentType("application/json");

    given()
        .auth().preemptive().basic(username, password)
        .header("X-XSRF-Header", "jksdhfkhdsf")
        .queryParam("op", "LISTSTATUS")
        .then()
        //.log().ifError()
        .statusCode(HttpStatus.SC_NOT_FOUND)
        //.content("FileStatuses.FileStatus[0].pathSuffix", is("app-logs"))
        .when().get(url + "/v1/");
    masterServer.isEmpty();

    LOG_EXIT();
  }


}
