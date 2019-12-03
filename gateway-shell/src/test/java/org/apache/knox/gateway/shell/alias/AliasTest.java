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

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.shell.KnoxSession;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AliasTest {

  @Test
  public void testAddGatewayAlias() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String alias = "aliasPut1";
    final String pwd   = "aliasPut1_pwd";

    final String expectedEndpointPath =
        session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + AbstractAliasRequest.GATEWAY_CLUSTER_NAME + "/" + alias;

    AbstractAliasRequest request = Alias.add(session, alias, pwd);
    assertTrue(request instanceof PostRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpPost.METHOD_NAME, request.getRequest().getMethod());

    HttpRequestBase httpRequest = request.getRequest();
    assertTrue(httpRequest instanceof HttpPost);
    HttpEntity entity = ((HttpPost) httpRequest).getEntity();
    assertNotNull("Missing expected form data.", entity);
    assertTrue(entity instanceof UrlEncodedFormEntity);
    String formData = null;
    try {
      formData = EntityUtils.toString(entity);
    } catch (IOException e) {
      fail("Failed to consume request entity: " + e.getMessage());
    }
    assertNotNull(formData);
    assertEquals("Form data mismatch",
                 PostRequest.FORM_PARAM_VALUE + "=" + pwd,
                 formData);
  }

  @Test
  public void testAddClusterAlias() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String clusterName = "myCluster";
    final String alias       = "aliasPut1";
    final String pwd         = "aliasPut1_pwd";

    final String expectedEndpointPath = session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + clusterName + "/" + alias;

    AbstractAliasRequest request = Alias.add(session, clusterName, alias, pwd);
    assertTrue(request instanceof PostRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpPost.METHOD_NAME, request.getRequest().getMethod());

    HttpRequestBase httpRequest = request.getRequest();
    assertTrue(httpRequest instanceof HttpPost);
    HttpEntity entity = ((HttpPost) httpRequest).getEntity();
    assertNotNull("Missing expected form data.", entity);
    assertTrue(entity instanceof UrlEncodedFormEntity);
    String formData = null;
    try {
      formData = EntityUtils.toString(entity);
    } catch (IOException e) {
      fail("Failed to consume request entity: " + e.getMessage());
    }
    assertNotNull(formData);
    assertEquals("Form data mismatch",
                 PostRequest.FORM_PARAM_VALUE + "=" + pwd,
                 formData);
  }

  @Test
  public void testRemoveGatewayAlias() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String alias = "aliasPut1";

    final String expectedEndpointPath =
        session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + AbstractAliasRequest.GATEWAY_CLUSTER_NAME + "/" + alias;

    AbstractAliasRequest request = Alias.remove(session, alias);
    assertTrue(request instanceof DeleteRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpDelete.METHOD_NAME, request.getRequest().getMethod());
  }

  @Test
  public void testRemoveClusterAlias() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String clusterName = "myCluster";
    final String alias       = "aliasPut1";

    final String expectedEndpointPath = session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + clusterName + "/" + alias;

    AbstractAliasRequest request = Alias.remove(session, clusterName, alias);
    assertTrue(request instanceof DeleteRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpDelete.METHOD_NAME, request.getRequest().getMethod());
  }

  @Test
  public void testListGatewayAliases() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String expectedEndpointPath =
        session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + AbstractAliasRequest.GATEWAY_CLUSTER_NAME;

    AbstractAliasRequest request = Alias.list(session);
    assertTrue(request instanceof ListRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpGet.METHOD_NAME, request.getRequest().getMethod());
  }


  @Test
  public void testListClusterAliases() {
    KnoxSession session = null;
    try {
      session = createMockKnoxSession();
    } catch (Exception e) {
      fail(e.getMessage());
    }

    final String clusterName = "myCluster";
    final String expectedEndpointPath = session.base() + AbstractAliasRequest.SERVICE_PATH + "/" + clusterName;

    AbstractAliasRequest request = Alias.list(session, clusterName);
    assertTrue(request instanceof ListRequest);
    assertEquals("Endpoint mismatch", expectedEndpointPath, request.getRequestURI().toString());

    Callable callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    assertEquals("Unexpected HTTP method.", HttpGet.METHOD_NAME, request.getRequest().getMethod());
  }


  private KnoxSession createMockKnoxSession() throws Exception {
    KnoxSession knoxSession = createMock(KnoxSession.class);
    expect(knoxSession.base()).andReturn("http://localhost/base").atLeastOnce();
    expect(knoxSession.getHeaders()).andReturn(Collections.emptyMap()).atLeastOnce();
    expect(knoxSession.executeNow(isA(HttpRequest.class))).andReturn(null).atLeastOnce();
    replay(knoxSession);
    return knoxSession;
  }


}
