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

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.identityasserter.common.filter.IdentityAsserterHttpServletRequestWrapper;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.apache.knox.test.mock.MockHttpServletRequest;
import org.apache.knox.test.mock.MockServletInputStream;
import org.junit.Test;
import org.junit.After;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@Category( { UnitTests.class, FastTests.class } )
public class IdentityAssertionHttpServletRequestWrapperTest {

  @After
  public void resetSystemProps() {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "false");
  }

  @Test
  public void testInsertUserNameInPostMethod() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInPostMethodWithoutEncoding() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInPostMethodWithIso88591Encoding() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setCharacterEncoding( StandardCharsets.ISO_8859_1.name() );
    request.setMethod("POST");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testOverwriteUserNameInPostMethod() throws IOException {
    String inputBody = "user.name=input-user&jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "user.name=output-user" ) );
    assertThat( output, not( containsString( "input-user" ) ) );
  }

  @Test
  public void testRemoveImpersonationParam() throws IOException {
    String inputBody = "DelegationUID=drwho&user.name=input-user";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setMethod("POST");

    // Add DelegationUID to impersonation list
    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user", Arrays.asList("DelegationUID"));

    String output = wrapper.getQueryString();
    assertThat( output, containsString( "user.name=output-user" ) );
    assertThat( output, not( containsString( "input-user" ) ) );
    assertThat( output, not( containsString( "drwho" ) ) );
  }

  @Test
  public void testIngoreNonFormBody() throws IOException {
    String inputBody = "user.name=input-user&jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaWebHCat%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( StandardCharsets.UTF_8 ) ) ) );
    request.setCharacterEncoding( StandardCharsets.UTF_8.name() );
    request.setContentType( "text/plain" );

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=input-user" ) );
    assertThat( outputBody, not( containsString( "output-user" ) ) );
  }

  @Test
  public void testInsertUserNameInQueryString() {
    String input = "param=value";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertDoAsInQueryString() {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "true");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString("op=LISTSTATUS&user.name=jack&User.Name=jill&DOas=admin&doas=root");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();
    assertThat(output, is("op=LISTSTATUS&doAs=output-user"));
  }

  @Test
  public void testInsertUserNameInNullQueryString() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInNullQueryStringForGET() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInQueryStringForPOST() {
    String input = null;

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );
    request.setMethod("POST");

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testOverwriteUserNameInQueryString() {
    String input = "user.name=input-user";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAsserterHttpServletRequestWrapper wrapper
        = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
    assertThat( output, not( containsString( "input-user" ) ) );
  }

  @Test
  public void testParameterWithNullValueInQueryString() {
    String input = "paramWithNullValue&param2=abc";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAsserterHttpServletRequestWrapper wrapper
      = new IdentityAsserterHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
    assertThat( output, containsString( "paramWithNullValue" ) );
    assertThat( output, containsString( "param2=abc" ) );
  }

  @Test
  public void testUrlEncode() {
    String s;
    HashMap<String,List<String>> m;

    m = new HashMap<>();
    m.put( "null-values", null );
    s = IdentityAsserterHttpServletRequestWrapper.urlEncode( m, StandardCharsets.UTF_8.name() );
    assertThat( s, is( "null-values" ) );

    m = new HashMap<>();
    m.put( "no-values", new ArrayList<>(0) );
    s = IdentityAsserterHttpServletRequestWrapper.urlEncode( m, StandardCharsets.UTF_8.name() );
    assertThat( s, is( "no-values" ) );

    m = new HashMap<>();
    List<String> lst = new ArrayList<>();
    lst.add("value1");
    m.put( "one-value", lst);
    s = IdentityAsserterHttpServletRequestWrapper.urlEncode( m, StandardCharsets.UTF_8.name() );
    assertThat( s, is( "one-value=value1" ) );

    m = new HashMap<>();
    String[] a = {"value1", "value2"};
    lst = new ArrayList<>(Arrays.asList(a));
    m.put( "two-values", lst);
    s = IdentityAsserterHttpServletRequestWrapper.urlEncode( m, StandardCharsets.UTF_8.name() );
    assertThat( s, is( "two-values=value1&two-values=value2" ) );
  }
}
