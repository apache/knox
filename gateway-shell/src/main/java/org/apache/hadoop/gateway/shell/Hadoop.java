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
package org.apache.hadoop.gateway.shell;

import groovy.lang.Closure;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Hadoop {

  String base;
  HttpHost host;
  DefaultHttpClient client;
  BasicHttpContext context;
  String username;
  String password;
  ExecutorService executor;

  public static Hadoop login( String url, String username, String password ) throws URISyntaxException {
    return new Hadoop( url, username, password );
  }

  private Hadoop( String url, String username, String password ) throws HadoopException, URISyntaxException {
    this.executor = Executors.newCachedThreadPool();
    this.base = url;
    this.username = username;
    this.password = password;

    URI uri = new URI( url );
    host = new HttpHost( uri.getHost(), uri.getPort(), uri.getScheme() );

    try {
      client = createClient();
      client.getCredentialsProvider().setCredentials(
          new AuthScope( host.getHostName(), host.getPort() ),
          new UsernamePasswordCredentials( username, password ) );
      AuthCache authCache = new BasicAuthCache();
      BasicScheme authScheme = new BasicScheme();
      authCache.put( host, authScheme );
      context = new BasicHttpContext();
      context.setAttribute( ClientContext.AUTH_CACHE, authCache );
    } catch( GeneralSecurityException e ) {
      throw new HadoopException( "Failed to create HTTP client.", e );
    }
  }

  private static DefaultHttpClient createClient() throws GeneralSecurityException {
    SchemeRegistry registry = new SchemeRegistry();
    SSLSocketFactory socketFactory = new SSLSocketFactory( new TrustSelfSignedStrategy(), SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER );
    registry.register( new Scheme( "https", 443, socketFactory ) );
    registry.register( new Scheme( "http", 80, new PlainSocketFactory() ) );
    PoolingClientConnectionManager mgr = new PoolingClientConnectionManager( registry );
    DefaultHttpClient client = new DefaultHttpClient( mgr, new DefaultHttpClient().getParams() );
    return client;
  }

  public String base() {
    return base;
  }

  public HttpResponse executeNow( HttpRequest request ) throws IOException {
    return client.execute( host, request, context );
  }

  public <T> Future<T> executeLater( Callable<T> callable ) {
    return executor.submit( callable );
  }

  public <T> void executeLater( final Callable<T> callable, final Closure<T> closure ) {
    executor.submit( new Callable<T>() {
      @Override
      public T call() throws Exception {
        closure.call( callable.call() );
        return null;
      }
    } );
  }

}
