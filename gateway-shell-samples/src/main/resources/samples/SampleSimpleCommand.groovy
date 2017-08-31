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
import org.apache.knox.gateway.shell.AbstractRequest
import org.apache.knox.gateway.shell.BasicResponse
import org.apache.knox.gateway.shell.Hadoop
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder

import java.util.concurrent.Callable

class SampleSimpleCommand extends AbstractRequest<BasicResponse> {

  SampleSimpleCommand( Hadoop hadoop ) {
    super( hadoop )
  }

  private String param
  SampleSimpleCommand param( String param ) {
    this.param = param
    return this
  }

  @Override
  protected Callable<BasicResponse> callable() {
    return new Callable<BasicResponse>() {
      @Override
      BasicResponse call() {
        URIBuilder uri = uri( SampleService.PATH, param )
        addQueryParam( uri, "op", "LISTSTATUS" )
        HttpGet get = new HttpGet( uri.build() )
        return new BasicResponse( execute( get ) )
      }
    }
  }

}