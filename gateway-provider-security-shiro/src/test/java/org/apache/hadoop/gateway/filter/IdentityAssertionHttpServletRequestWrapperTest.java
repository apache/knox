/**
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
package org.apache.hadoop.gateway.filter;

import org.apache.commons.io.IOUtils;
import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.apache.hadoop.test.mock.MockHttpServletRequest;
import org.apache.hadoop.test.mock.MockServletInputStream;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@Category( { UnitTests.class, FastTests.class } )
public class IdentityAssertionHttpServletRequestWrapperTest {

  @Test
  public void testInsertUserNameInFormParam() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( "UTF-8" ) ) ) );
    request.setCharacterEncoding( "UTF-8" );
    request.setContentType( "application/x-www-form-urlencoded" );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInFormParamWithoutEncoding() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( "UTF-8" ) ) ) );
    request.setContentType( "application/x-www-form-urlencoded" );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testInsertUserNameInFormParamWithIso88591Encoding() throws IOException {
    String inputBody = "jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( "UTF-8" ) ) ) );
    request.setContentType( "application/x-www-form-urlencoded" );
    request.setCharacterEncoding( "ISO-8859-1" );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testOverwriteUserNameInFormParam() throws IOException {
    String inputBody = "user.name=input-user&jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( "UTF-8" ) ) ) );
    request.setCharacterEncoding( "UTF-8" );
    request.setContentType( "application/x-www-form-urlencoded" );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=output-user" ) );
    assertThat( outputBody, not( containsString( "input-user" ) ) );
  }

  @Test
  public void testIngoreNonFormBody() throws IOException {
    String inputBody = "user.name=input-user&jar=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Fhadoop-examples.jar&class=org.apache.org.apache.hadoop.examples.WordCount&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Finput&arg=%2Ftmp%2FGatewayWebHdfsFuncTest%2FtestJavaMapReduceViaTempleton%2Foutput";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setInputStream( new MockServletInputStream( new ByteArrayInputStream( inputBody.getBytes( "UTF-8" ) ) ) );
    request.setCharacterEncoding( "UTF-8" );
    request.setContentType( "text/plain" );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String outputBody = IOUtils.toString( wrapper.getInputStream(), wrapper.getCharacterEncoding() );

    assertThat( outputBody, containsString( "user.name=input-user" ) );
    assertThat( outputBody, not( containsString( "output-user" ) ) );
  }

  @Test
  public void testInsertUserNameInQueryString() {
    String input = "param=value";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
  }

  @Test
  public void testOverwriteUserNameInQueryString() {
    String input = "user.name=input-user";

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setQueryString( input );

    IdentityAssertionHttpServletRequestWrapper wrapper
        = new IdentityAssertionHttpServletRequestWrapper( request, "output-user" );

    String output = wrapper.getQueryString();

    assertThat( output, containsString( "user.name=output-user" ) );
    assertThat( output, not( containsString( "input-user" ) ) );
  }

}
