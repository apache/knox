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
package org.apache.knox.gateway.dispatch;

import org.apache.http.client.HttpClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public interface Dispatch {

  void init();

  void destroy();

  HttpClient getHttpClient();

  void setHttpClient(HttpClient httpClient);

  URI getDispatchUrl( HttpServletRequest request );

  void doGet( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

  void doPost( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

  void doPut( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

  void doDelete( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

  void doOptions( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

  /**
   * @since 0.14.0
   */
  void doHead( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, ServletException, URISyntaxException;

}
