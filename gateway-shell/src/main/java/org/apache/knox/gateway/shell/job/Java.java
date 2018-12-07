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
package org.apache.knox.gateway.shell.job;

import com.jayway.jsonpath.JsonPath;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

class Java {
  public static class Request extends AbstractRequest<Response> {

    String jar;
    String app;
    String input;
    String output;
    List<NameValuePair> params = new ArrayList<>();

    Request( KnoxSession session ) {
      super( session );
    }

    public Request jar( String jar ) {
      this.jar = jar;
      return this;
    }

    public Request app( String app ) {
      this.app = app;
      return this;
    }

    public Request input( String dir ) {
      input = dir;
      return this;
    }

    public Request output( String dir ) {
      output = dir;
      return this;
    }

    public Request arg( String value ) {
      addParam( params, "arg", value );
      return this;
    }

    @Override
    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          URIBuilder uri = uri( Job.SERVICE_PATH, "/mapreduce/jar" );
          params.add( new BasicNameValuePair( "jar", jar ) );
          params.add( new BasicNameValuePair( "class", app ) );
          params.add( new BasicNameValuePair( "arg", input ) );
          params.add( new BasicNameValuePair( "arg", output ) );
          UrlEncodedFormEntity form = new UrlEncodedFormEntity( params );
          HttpPost request = new HttpPost( uri.build() );
          request.setEntity( form );
          return new Response( execute( request ) );
        }
      };
    }

  }

  public static class Response extends BasicResponse {
    Response( HttpResponse response ) {
      super( response );
    }

    public String getJobId() throws IOException {
      return JsonPath.read( getString(), "$.id" );
    }

  }

}
