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

import org.apache.hadoop.gateway.shell.AbstractRequest;
import org.apache.hadoop.gateway.shell.AbstractResponse;
import org.apache.hadoop.gateway.shell.Hadoop;
import org.apache.hadoop.gateway.shell.HadoopException;
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
import java.io.IOException;
import java.net.URISyntaxException;

class Put {

  static class Request extends AbstractRequest {

    String text;
    String from;
    String to;

    Request( Hadoop hadoop ) {
      super( hadoop );
    }

    public Request text( String text ) {
      this.text = text;
      return this;
    }

    public Request file( String file ) {
      this.from = file;
      return this;
    }

    public Request to( String file ) {
      this.to = file;
      return this;
    }

    public Response now() throws IOException, URISyntaxException {
      URIBuilder uri = uri( Hdfs.SERVICE_PATH, to );
      addQueryParam( uri, "op", "CREATE" );
      HttpPut nn = new HttpPut( uri.build() );
      HttpResponse r = execute( nn );
      if( r.getStatusLine().getStatusCode() != HttpStatus.SC_TEMPORARY_REDIRECT ) {
        throw new HadoopException( r.getStatusLine().toString() );
      }
      EntityUtils.consumeQuietly( r.getEntity() );
      Header[] h = r.getHeaders( "Location" );
      if( h == null || h.length != 1 ) {
        throw new HadoopException( "Invalid Location header." );
      }
      String loc = h[0].getValue();
      HttpPut dn = new HttpPut( loc );
      HttpEntity e = null;
      if( text != null ) {
        e = new StringEntity( text );
      } else if( from != null ) {
        e = new FileEntity( new File( from ) );
      }
      dn.setEntity( e );
      return new Response( execute( dn ) );
    }

  }

  static class Response extends AbstractResponse {

    Response( HttpResponse response ) {
      super( response );
      consume();
    }

  }

}
