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
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

public class TruncateTable {
  public static class Request extends AbstractRequest<TruncateTable.Response> {

    private String tableName;

    public Request(KnoxSession session, String tableName) {
      super(session);
      this.tableName = tableName;
    }

    @Override
    protected Callable<TruncateTable.Response> callable() {
      return new Callable<TruncateTable.Response>() {
        @Override
        public TruncateTable.Response call() throws Exception {

          URI uri = uri(HBase.SERVICE_PATH, "/", tableName, "/schema").build();

          String schema;
          HttpGet get = new HttpGet(uri);
          get.setHeader("Accept", "text/xml");
          try (CloseableHttpResponse getResponse = execute(get)) {
            schema = EntityUtils.toString(getResponse.getEntity());
          }

          HttpDelete delete = new HttpDelete(uri);
          try (CloseableHttpResponse deleteResponse = execute(delete)) {
            EntityUtils.consumeQuietly(deleteResponse.getEntity());
          }

          HttpPut put = new HttpPut(uri);
          HttpEntity entity = new StringEntity(schema, ContentType.create("text/xml", StandardCharsets.UTF_8));
          put.setEntity(entity);
          return new TruncateTable.Response(execute(put));
        }
      };
    }
  }

  public static class Response extends EmptyResponse {
    Response(HttpResponse response) {
      super(response);
    }
  }
}
