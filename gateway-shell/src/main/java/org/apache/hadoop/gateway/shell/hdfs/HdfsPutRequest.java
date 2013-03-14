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
package org.apache.hadoop.gateway.shell.hdfs;

import com.jayway.restassured.response.Response;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.gateway.shell.AbstractRequest;
import org.apache.hadoop.gateway.shell.hadoop.Hadoop;

import java.io.File;
import java.io.IOException;

import static com.jayway.restassured.RestAssured.with;

public class HdfsPutRequest extends AbstractRequest {

  String from;
  String to;

  HdfsPutRequest( Hadoop hadoop ) {
    super( hadoop );
  }

  public HdfsPutRequest from( String file ) {
    this.from = file;
    return this;
  }

  public HdfsPutRequest to( String file ) {
    this.to = file;
    return this;
  }

  public HdfsPutResponse go() throws IOException {
    Response response = with().spec( hadoop().request() ).queryParam( "op", "CREATE" )
        .expect().statusCode( 307 ).when().put( Hdfs.SERVICE_PATH + to );
    String url = response.getHeader( "Location" );
    request().body( FileUtils.readFileToByteArray( new File( from ) ) );
    return new HdfsPutResponse( request().put( url ) );
  }

}
