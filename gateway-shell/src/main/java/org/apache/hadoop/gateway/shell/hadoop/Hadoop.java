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
package org.apache.hadoop.gateway.shell.hadoop;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.with;

public class Hadoop {

  String username;
  String password;

  public static Hadoop login( String baseUri, String username, String password ) {
    RestAssured.baseURI = baseUri;
    return new Hadoop( username, password );
  }

  public RequestSpecification request() {
    //return with().log().all().auth().preemptive().basic( username, password );
    return with().auth().preemptive().basic( username, password );
  }

  public ResponseSpecification response() {
    return expect(); //.log().all();
  }

  private Hadoop( String username, String password ) {
    this.username = username;
    this.password = password;
  }

}
