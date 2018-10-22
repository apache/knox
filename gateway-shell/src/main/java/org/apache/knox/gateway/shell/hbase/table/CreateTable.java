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
package org.apache.knox.gateway.shell.hbase.table;

import org.apache.knox.gateway.shell.AbstractRequest;
import org.apache.knox.gateway.shell.EmptyResponse;
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.knox.gateway.util.XmlUtils;
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

public class CreateTable {

  public static class Request extends AbstractRequest<Response> implements FamilyContainer<Request> {

    private static final String ELEMENT_TABLE_SCHEMA = "TableSchema";
    private static final String ELEMENT_COLUMN_SCHEMA = "ColumnSchema";
    private static final String ATTRIBUTE_NAME = "name";

    private String tableName;
    private List<Attribute> attributes = new ArrayList<>();
    private List<Family<Request>> families = new ArrayList<Family<Request>>();

    public Request( Hadoop session, String tableName ) {
      super( session );
      this.tableName = tableName;
    }

    public Request attribute( String name, Object value ) {
      attributes.add( new Attribute( name, value ) );
      return this;
    }

    public Family<Request> family( String name ) {
      Family<Request> family = new Family<>( this, name );
      families.add( family );
      return family;
    }

    @Override
    public Request addFamily( Family<Request> family ) {
      families.add( family );
      return this;
    }

    protected Callable<Response> callable() {
      return new Callable<Response>() {
        @Override
        public Response call() throws Exception {
          Document document = XmlUtils.createDocument();

          Element root = document.createElement( ELEMENT_TABLE_SCHEMA );
          root.setAttribute( ATTRIBUTE_NAME, tableName );
          for( Attribute attribute : attributes ) {
            root.setAttribute( attribute.getName(), attribute.getValue().toString() );
          }
          document.appendChild( root );

          for( Family<Request> family : families ) {
            Element columnSchema = document.createElement( ELEMENT_COLUMN_SCHEMA );
            columnSchema.setAttribute( ATTRIBUTE_NAME, family.name() );
            for( Attribute attribute : family.attributes() ) {
              columnSchema.setAttribute( attribute.getName(), attribute.getValue().toString() );
            }
            root.appendChild( columnSchema );
          }

          StringWriter writer = new StringWriter();
          Transformer t = XmlUtils.getTransformer( true, false, 0, false );
          XmlUtils.writeXml( document, writer, t );

          URIBuilder uri = uri( HBase.SERVICE_PATH, "/", tableName, "/schema" );
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
  }
}
