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
package org.apache.hadoop.gateway.filter.rewrite.impl.json;

import com.jayway.jsonassert.JsonAssert;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class JsonFilterReaderTest {

  @Test
  public void testSimple() throws IOException {
    String inputJson = "{ \"name\" : \"value\" }";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new JsonFilterReader( inputReader );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    JsonAssert.with( outputJson ).assertThat( "name", is( "value" ) );
  }

  @Test
  public void testEmptyObject() throws IOException {
    String inputJson = "{}";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new JsonFilterReader( inputReader );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    assertThat( outputJson, is( "{}" ) );
  }

  @Test
  public void testEmptyArray() throws IOException {
    String inputJson = "[]";
    StringReader inputReader = new StringReader( inputJson );
    JsonFilterReader filterReader = new JsonFilterReader( inputReader );
    String outputJson = new String( IOUtils.toCharArray( filterReader ) );

    assertThat( outputJson, is( "[]" ) );
  }

}
