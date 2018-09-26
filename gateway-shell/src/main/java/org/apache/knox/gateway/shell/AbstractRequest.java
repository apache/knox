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
package org.apache.knox.gateway.shell;

import groovy.lang.Closure;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public abstract class AbstractRequest<T> {

  private Hadoop session;

  protected AbstractRequest( Hadoop session ) {
    this.session = session;
  }

  protected Hadoop hadoop() {
    return session;
  }

  protected CloseableHttpResponse execute(HttpRequest request ) throws IOException {
    addHeaders(request, session.getHeaders());
    return session.executeNow( request );
  }

  /**
   * @param request
   * @param headers
   */
  private void addHeaders(HttpRequest request, Map<String, String> headers) {
    for(Entry<String, String> header : headers.entrySet()) {
      request.setHeader(header.getKey(), header.getValue());
    }
  }

  protected URIBuilder uri( String... parts ) throws URISyntaxException {
    return new URIBuilder( session.base() + StringUtils.join( parts ) );
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

  abstract protected Callable<T> callable();

  public T now() throws HadoopException {
    try {
      return callable().call();
    } catch( Exception e ) {
      throw new HadoopException( e );
    }
  }

  public Future<T> later() {
    return hadoop().executeLater( callable() );
  }

  public Future<T> later( final Closure<Void> closure ) {
    return hadoop().executeLater( new Callable<T>() {
      @Override
      public T call() throws Exception {
        T result = callable().call();
        closure.call( result );
        return result;
      }
    } );
  }

}
