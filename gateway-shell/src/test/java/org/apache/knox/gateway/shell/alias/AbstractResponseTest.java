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
package org.apache.knox.gateway.shell.alias;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import org.apache.knox.gateway.shell.KnoxShellException;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractResponseTest<T extends AliasResponse> {

  @Test
  public void testInvalidResponseStatus() {
    // Anything other than the expected response status
    HttpResponse httpResponse =
        createTestResponse(createTestStatusLine(HttpStatus.SC_BAD_REQUEST, "Bad Request"), null);

    try {
      createResponse(httpResponse);
    } catch (KnoxShellException e) {
      // Expected
      assertEquals("Unexpected response: " + httpResponse.getStatusLine().getReasonPhrase(), e.getMessage());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testMissingResponseEntity() {
    HttpResponse httpResponse = createTestResponse(createTestStatusLine(getExpectedResponseStatusCode()), null);

    try {
      createResponse(httpResponse);
    } catch (KnoxShellException e) {
      // Expected
      assertEquals("Missing expected response content", e.getMessage());
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInvalidResponseJSON() {
    StringEntity entity = null;
    try {
      entity = new StringEntity("{ \"topology\": \"testInvalidJSONCluster\", \"aliases\" : [ ");
      entity.setContentType("application/json");
    } catch (UnsupportedEncodingException e) {
      fail(e.getMessage());
    }

    HttpResponse httpResponse = createTestResponse(createTestStatusLine(getExpectedResponseStatusCode()), entity);

    try {
      createResponse(httpResponse);
    } catch (KnoxShellException e) {
      // Expected
      assertEquals("Unable to process response content", e.getMessage());
      assertTrue(e.getCause().getMessage().contains("Unexpected end-of-input"));
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testInvalidResponseContentType() {
    StringEntity entity = null;
    try {
      entity = new StringEntity("{ \"topology\": \"testInvalidJSONCluster\", \"aliases\" : [ ");
    } catch (UnsupportedEncodingException e) {
      fail(e.getMessage());
    }

    HttpResponse httpResponse = createTestResponse(createTestStatusLine(getExpectedResponseStatusCode()), entity);

    try {
      createResponse(httpResponse);
    } catch (KnoxShellException e) {
      // Expected
      assertTrue(e.getMessage().startsWith("Unexpected response content type: "));
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }


  abstract StringEntity createTestEntity(String cluster, List<String> aliases) throws Exception;


  abstract T createResponse(HttpResponse httpResponse);


  int getExpectedResponseStatusCode() {
    return HttpStatus.SC_OK;
  }


  HttpResponse createTestResponse(final StatusLine   statusLine,
                                  final String       cluster,
                                  final List<String> aliases) {
    StringEntity entity = null;
    try {
      entity = createTestEntity(cluster, aliases);
      entity.setContentType("application/json");
    } catch (Exception e) {
      fail(e.getMessage());
    }
    return createTestResponse(statusLine, entity);
  }


  HttpResponse createTestResponse(final StatusLine statusLine, final StringEntity entity) {
    HttpResponse httpResponse = EasyMock.createNiceMock(HttpResponse.class);
    EasyMock.expect(httpResponse.getStatusLine()).andReturn(statusLine).anyTimes();
    EasyMock.expect(httpResponse.getEntity()).andReturn(entity).anyTimes();
    EasyMock.replay(httpResponse);
    return httpResponse;
  }


  StatusLine createTestStatusLine(int statusCode) {
    return createTestStatusLine(statusCode, null);
  }


  StatusLine createTestStatusLine(int statusCode, final String reasonPhrase) {
    StatusLine statusLine = EasyMock.createNiceMock(StatusLine.class);
    EasyMock.expect(statusLine.getStatusCode()).andReturn(statusCode).anyTimes();
    EasyMock.expect(statusLine.getReasonPhrase()).andReturn(reasonPhrase).anyTimes();
    EasyMock.replay(statusLine);
    return statusLine;
  }

}
