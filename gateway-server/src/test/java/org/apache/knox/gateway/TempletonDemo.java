/*
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
package org.apache.knox.gateway;

import org.apache.knox.test.category.ManualTests;
import org.apache.knox.test.category.SlowTests;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.helpers.Loader;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

@Category( { ManualTests.class, SlowTests.class } )
public class TempletonDemo {

  @Test
  public void demoDirect() throws IOException {
    demo( "http://vm:50111/templeton/v1/mapreduce/jar" );
  }

  @Test
  public void demoGateway() throws IOException {
    URL url = Loader.getResource( "log4j.properties" );
    System.out.println( url );
    demo( "http://localhost:8888/gateway/cluster/templeton/v1/mapreduce/jar" );
  }

  private void demo( String url ) throws IOException {
    List<NameValuePair> parameters = new ArrayList<>();
    parameters.add( new BasicNameValuePair( "user.name", "hdfs" ) );
    parameters.add( new BasicNameValuePair( "jar", "wordcount/org.apache.hadoop-examples.jar" ) );
    parameters.add( new BasicNameValuePair( "class", "org.apache.org.apache.hadoop.examples.WordCount" ) );
    parameters.add( new BasicNameValuePair( "arg", "wordcount/input" ) );
    parameters.add( new BasicNameValuePair( "arg", "wordcount/output" ) );
    UrlEncodedFormEntity entity = new UrlEncodedFormEntity( parameters, Charset.forName( "UTF-8" ) );
    HttpPost request = new HttpPost( url );
    request.setEntity( entity );

    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    HttpResponse response = client.execute( request );
    System.out.println( EntityUtils.toString( response.getEntity() ) );
  }

}
