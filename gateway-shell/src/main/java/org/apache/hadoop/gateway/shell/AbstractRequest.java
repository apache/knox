package org.apache.hadoop.gateway.shell;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

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
public abstract class AbstractRequest {

  private Hadoop hadoop;

  public AbstractRequest( Hadoop hadoop ) {
    this.hadoop = hadoop;
  }

  protected Hadoop hadoop() {
    return hadoop;
  }

  protected HttpResponse execute( HttpRequest request ) throws IOException {
    return hadoop.execute( request );
  }

  protected URIBuilder uri( String... parts ) throws URISyntaxException {
    return new URIBuilder( hadoop.base() + StringUtils.join( parts ) );
  }

  protected void addQueryParam( URIBuilder uri, String name, Object value ) {
    if( value != null ) {
      uri.addParameter( name, value.toString() );
    }
  }

  protected void addParam( List<NameValuePair> list, String name, String value ) {
    if( value != null ) {
      list.add( new BasicNameValuePair( name, value ) );
    }
  }

}
