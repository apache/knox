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
package org.apache.knox.gateway.identityasserter.filter;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.test.mock.MockHttpServletRequest;
import org.apache.knox.test.mock.MockServletInputStream;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

public class NoImpersonationHttpServletRequestWrapperTest {
  @After
  public void resetSystemProps() {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "false");
  }

  @Test
  public void testNoUserNameInPostMethod() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "" ) );
  }

  @Test
  public void testNoUserNameInPostMethodWithoutEncoding() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    // make sure principal is not added
    assertTrue(StringUtils.isBlank(output));
  }

  @Test
  public void testOverwriteUserNameInPostMethod() throws IOException {
    String inputBody = "user.name=input-user&jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "" ) );
    assertThat( output, not( containsString( "input-user" ) ) );
  }


  @Test
  public void testInsertUserNameInQueryString() {
    String input = "param=value";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "" ) );
  }

  @Test
  public void testInsertNoDoAsInQueryString() {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "true");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString("op=LISTSTATUS&user.name=jack&User.Name=jill&DOas=admin&doas=root");

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat(output, is("op=LISTSTATUS"));
  }

  @Test
  public void testNoUserNameInNullQueryString() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "" ) );
  }

  @Test
  public void testNoUserNameInNullQueryStringForGET() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "" ) );
  }

  @Test
  public void testNoUserNameInQueryStringForPOST() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );
    request.setMethod("POST");

    NoImpersonationAsserterRequestWrapper wrapper
        = new NoImpersonationAsserterRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "" ) );
  }
}
