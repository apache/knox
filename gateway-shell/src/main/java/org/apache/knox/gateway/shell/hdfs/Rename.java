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
import org.apache.knox.gateway.shell.Hadoop;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;

import java.util.concurrent.Callable;

public class Rename {

  public static class Request extends AbstractRequest<Rename.Response> {

    private String file;
    private String to;

    Request(Hadoop session) {
      super(session);
    }

    public Rename.Request file(String file) {
      this.file = file;
      return this;
    }

    public Rename.Request to(String file) {
      this.to = file;
      return this;
    }

    protected Callable<Rename.Response> callable() {
      return new Callable<Rename.Response>() {
        @Override
        public Rename.Response call() throws Exception {
          URIBuilder uri = uri(Hdfs.SERVICE_PATH, file);
          addQueryParam(uri, "op", "RENAME");
          addQueryParam(uri, "destination", to);
          HttpPut request = new HttpPut(uri.build());
          return new Rename.Response(execute(request));
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