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
package org.apache.hadoop.gateway.util.uritemplate;

import org.junit.Test;

import java.net.URI;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class RewriterTest {

  @Test
  public void testBasicRewrite() throws Exception {
    Rewriter rewriter = new Rewriter();
    Parser parser = new Parser();
    URI inputUri, outputUri;
    Template inputTemplate, outputTemplate;
    Resolver resolver = new Params();

    inputUri = new URI( "path-1/path-2" );
    inputTemplate = parser.parse( "{path-1-name}/{path-2-name}" );
    outputTemplate = parser.parse( "{path-2-name}/{path-1-name}" );
    outputUri = rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "path-2/path-1" ) );

    inputUri = new URI( "some-path?query-name=some-param-value" );
    inputTemplate = parser.parse( "{path-name}?query-name={param-value}" );
    outputTemplate = parser.parse( "{param-value}/{path-name}" );
    outputUri = rewriter.rewrite( inputUri, inputTemplate, outputTemplate, resolver );
    assertThat( outputUri.toString(), equalTo( "some-param-value/some-path" ) );
  }

}
