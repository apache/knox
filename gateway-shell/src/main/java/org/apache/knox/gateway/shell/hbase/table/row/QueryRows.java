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
import org.apache.knox.gateway.shell.BasicResponse;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class QueryRows {

  public static class Request extends AbstractRequest<Response> {

    private String rowsId;
    private String tableName;
    private List<Column> columns = new ArrayList<>();
    private Long startTime;
    private Long endTime;
    private Long numVersions;

    public Request( KnoxSession session, String rowsId, String tableName ) {
      super( session );
      this.rowsId = rowsId;
      this.tableName = tableName;
    }

    public Request column( String family, String qualifier ) {
      Column column = new Column( family, qualifier );
      columns.add( column );
      return this;
    }

    public Request column( String family ) {
      return column( family, null );
    }

    public Request startTime( Long startTime ) {
      this.startTime = startTime;
      return this;
    }

    public Request endTime( Long endTime ) {
      this.endTime = endTime;
      return this;
    }

    public Request times( Long startTime, Long endTime ) {
      this.startTime = startTime;
      this.endTime = endTime;
      return this;
    }

    public Request numVersions( Long numVersions ) {
      this.numVersions = numVersions;
      return this;
    }

    @Override
    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          String rowsIdToQuery = rowsId;
          if( rowsIdToQuery == null || rowsIdToQuery.isEmpty() ) {
            rowsIdToQuery = "*";
          }

          StringBuilder columnsURIPart = new StringBuilder( "/" );
          int index = 0;
          for( Column column : columns ) {
            if( index++ > 0 ) {
              columnsURIPart.append(',');
            }
            columnsURIPart.append( column.toURIPart() );
          }
          columnsURIPart.append('/');

          StringBuilder timesURIPart = new StringBuilder();
          if( startTime != null && endTime != null ) {
            timesURIPart.append( startTime ).append(',').append( endTime );
          } else if( startTime != null ) {
            timesURIPart.append( startTime ).append(',').append( Long.MAX_VALUE );
          } else if( endTime != null ) {
            timesURIPart.append( endTime );
          }
          StringBuilder versionURIPart = new StringBuilder();
          if( numVersions != null ) {
            versionURIPart.append( "?v=" ).append( numVersions );
          }

          URIBuilder uri = uri( HBase.SERVICE_PATH, "/", tableName, "/", rowsIdToQuery, columnsURIPart.toString(), timesURIPart.toString(), versionURIPart.toString() );
          HttpGet get = new HttpGet( uri.build() );
          get.setHeader( "Accept", "application/json" );
          return new Response( execute( get ) );
        }
      };
    }
  }

  public static class Response extends BasicResponse {

    Response( HttpResponse response ) {
      super( response );
    }
  }
}
