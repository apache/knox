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
package org.apache.knox.gateway.shell.hdfs;

import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.EmptyResponse;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.KnoxShellException;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.util.concurrent.Callable;

public class Put {

  public static class Request extends AbstractRequest<Response> {

    private String text;
    private String file;
    private String to;
    private boolean overwrite;
    private int permission = 755;
    private Integer blocksize;
    private Integer buffersize;
    private Short replication;

    Request( KnoxSession session ) {
      super( session );
    }

    public Request text( String text ) {
      this.text = text;
      return this;
    }

    public Request file( String file ) {
      this.file = file;
      return this;
    }

    public Request to( String file ) {
      this.to = file;
      return this;
    }

    public Request overwrite( boolean overwrite ) {
      this.overwrite = overwrite;
      return this;
    }

    public Request permission( int permission ) {
      this.permission = permission;
      return this;
    }

    public Request blocksize( Integer blocksize ) {
      this.blocksize = blocksize;
      return this;
    }

    public Request replication( Short replication ) {
      this.replication = replication;
      return this;
    }

    public Request buffersize( Integer buffersize ) {
      this.buffersize = buffersize;
      return this;
    }

    @Override
    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          URIBuilder uri = uri( Hdfs.SERVICE_PATH, to );
          addQueryParam( uri, "op", "CREATE" );
          addQueryParam( uri, "overwrite", overwrite );
          addQueryParam( uri, "permission", permission );
          addQueryParam( uri, "blocksize", blocksize );
          addQueryParam( uri, "replication", replication );
          addQueryParam( uri, "buffersize", buffersize );
          HttpPut nn = new HttpPut( uri.build() );
          HttpResponse r = execute( nn );
          if( r.getStatusLine().getStatusCode() != HttpStatus.SC_TEMPORARY_REDIRECT ) {
            throw new KnoxShellException( r.getStatusLine().toString() );
          }
          EntityUtils.consumeQuietly( r.getEntity() );
          Header[] h = r.getHeaders( "Location" );
          if( h == null || h.length != 1 ) {
            throw new KnoxShellException( "Invalid Location header." );
          }
          String loc = h[0].getValue();
          HttpPut dn = new HttpPut( loc );
          HttpEntity e = null;
          if( text != null ) {
            e = new StringEntity( text );
          } else if( file != null ) {
            e = new FileEntity( new File( file ) );
          }
          dn.setEntity( e );
          return new Response( execute( dn ) );
        }
      };
    }

  }

  public static class Response extends EmptyResponse {

    Response( HttpResponse response ) {
      super( response );
    }

  }

}
