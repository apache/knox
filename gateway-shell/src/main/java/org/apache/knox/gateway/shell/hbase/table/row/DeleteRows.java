/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.shell.hbase.table.row;

import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.EmptyResponse;
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.utils.URIBuilder;

import java.util.concurrent.Callable;

public class DeleteRows {

  public static class Request extends AbstractRequest<Response> {

    private String rowsId;
    private String tableName;
    private Column column;
    private Long time;

    public Request( Hadoop session, String rowsId, String tableName ) {
      super( session );
      this.rowsId = rowsId;
      this.tableName = tableName;
    }

    public Request column( String family, String qualifier ) {
      column = new Column( family, qualifier );
      return this;
    }

    public Request column( String family ) {
      column = new Column( family );
      return this;
    }

    public Request time( Long time ) {
      this.time = time;
      return this;
    }

    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          String rowsIdToQuery = rowsId;
          if( rowsIdToQuery == null || rowsIdToQuery.isEmpty() ) {
            rowsIdToQuery = "*";
          }

          StringBuilder columnsURIPart = new StringBuilder( "/" );
          if( column != null ) {
            columnsURIPart.append( column.toURIPart() );
          }
          columnsURIPart.append( "/" );

          String timeURIPart = "";
          if( time != null ) {
            timeURIPart = time.toString();
          }

          URIBuilder uri = uri( HBase.SERVICE_PATH, "/", tableName, "/", rowsIdToQuery, columnsURIPart.toString(), timeURIPart );
          HttpDelete delete = new HttpDelete( uri.build() );
          return new Response( execute( delete ) );
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
