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
package org.apache.knox.gateway.shell.hbase.table.scanner;

import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.EmptyResponse;
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.knox.gateway.shell.hbase.table.row.Column;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.xml.transform.Transformer;

public class CreateScanner {

  public static class Request extends AbstractRequest<Response> {

    private static final String ELEMENT_SCANNER = "Scanner";
    private static final String ELEMENT_COLUMN = "column";
    private static final String ELEMENT_FILTER = "filter";
    private static final String ATTRIBUTE_START_ROW = "startRow";
    private static final String ATTRIBUTE_END_ROW = "endRow";
    private static final String ATTRIBUTE_BATCH = "batch";
    private static final String ATTRIBUTE_START_TIME = "startTime";
    private static final String ATTRIBUTE_END_TIME = "endTime";
    private static final String ATTRIBUTE_MAX_VERSIONS = "maxVersions";

    private String tableName;
    private String startRow;
    private String endRow;
    private List<Column> columns = new ArrayList<>();
    private Integer batch;
    private Long startTime;
    private Long endTime;
    private String filter;
    private Integer maxVersions;

    public Request( Hadoop session, String tableName ) {
      super( session );
      this.tableName = tableName;
    }

    public Request rows( String startRow, String endRow ) {
      this.startRow = startRow;
      this.endRow = endRow;
      return this;
    }

    public Request startRow( String startRow ) {
      this.startRow = startRow;
      return this;
    }

    public Request endRow( String endRow ) {
      this.endRow = endRow;
      return this;
    }

    public Request column( String family, String qualifier ) {
      Column column = new Column( family, qualifier );
      columns.add( column );
      return this;
    }

    public Request column( String family ) {
      return column( family, null );
    }

    public Request batch( Integer batch ) {
      this.batch = batch;
      return this;
    }

    public Request times( Long startTime, Long endTime ) {
      this.startTime = startTime;
      this.endTime = endTime;
      return this;
    }

    public Request startTime( Long startTime ) {
      this.startTime = startTime;
      return this;
    }

    public Request endTime( Long endTime ) {
      this.endTime = endTime;
      return this;
    }

    public Request filter( String filter ) {
      this.filter = filter;
      return this;
    }

    public Request maxVersions( Integer maxVersions ) {
      this.maxVersions = maxVersions;
      return this;
    }

    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          Document document = XmlUtils.createDocument();

          Element root = document.createElement( ELEMENT_SCANNER );
          if( startRow != null ) {
            root.setAttribute( ATTRIBUTE_START_ROW, Base64.encodeBase64String( startRow.getBytes( "UTF-8" ) ) );
          }
          if( endRow != null ) {
            root.setAttribute( ATTRIBUTE_END_ROW, Base64.encodeBase64String( endRow.getBytes( "UTF-8" ) ) );
          }
          if( batch != null ) {
            root.setAttribute( ATTRIBUTE_BATCH, batch.toString() );
          }
          if( startTime != null ) {
            root.setAttribute( ATTRIBUTE_START_TIME, startTime.toString() );
          }
          if( endTime != null ) {
            root.setAttribute( ATTRIBUTE_END_TIME, endTime.toString() );
          }
          if( maxVersions != null ) {
            root.setAttribute( ATTRIBUTE_MAX_VERSIONS, maxVersions.toString() );
          }
          document.appendChild( root );

          for( Column column : columns ) {
            Element columnElement = document.createElement( ELEMENT_COLUMN );
            columnElement.setTextContent( Base64.encodeBase64String( column.toURIPart().getBytes( "UTF-8" ) ) );
            root.appendChild( columnElement );
          }

          if( filter != null && !filter.isEmpty() ) {
            Element filterElement = document.createElement( ELEMENT_FILTER );
            filterElement.setTextContent( filter );
            root.appendChild( filterElement );
          }

          StringWriter writer = new StringWriter();
          Transformer t = XmlUtils.getTransformer( true, false, 0, false );
          XmlUtils.writeXml( document, writer, t );

          URIBuilder uri = uri( HBase.SERVICE_PATH, "/", tableName, "/scanner" );
          HttpPut request = new HttpPut( uri.build() );
          HttpEntity entity = new StringEntity( writer.toString(), ContentType.create( "text/xml", "UTF-8" ) );
          request.setEntity( entity );

          return new Response( execute( request ) );
        }
      };
    }
  }

  public static class Response extends EmptyResponse {

    Response( HttpResponse response ) {
      super( response );
    }

    public String getScannerId() {
      Header locationHeader = response().getFirstHeader( "Location" );
      if( locationHeader != null && locationHeader.getValue() != null && !locationHeader.getValue().isEmpty() ) {
        String location = locationHeader.getValue();
        int position = location.lastIndexOf( "/" );
        if( position != -1 ) {
          return location.substring( position + 1 );
        }
      }
      return null;
    }
  }
}
