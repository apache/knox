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
package org.apache.knox.gateway.util;

import org.junit.Test;

import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParseException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MimeTypeMapTest {

  @Test
  public void testTypeFallback() throws MimeTypeParseException {
     MimeTypeMap<String> map = new MimeTypeMap<>();

    map.put( new MimeType( "text/xml" ), "text/xml" );
    map.put( new MimeType( "text/json" ), "text/json" );
    map.put( new MimeType( "text/*" ), "text/*" );

    map.put( new MimeType( "application/json" ), "text/json" );
    map.put( new MimeType( "application/*" ), "application/*" );

    map.put( new MimeType( "*/xml" ), "*/xml" );
    map.put( new MimeType( "*/json" ), "*/json" );
    map.put( new MimeType( "*/*" ), "*/*" );

    assertThat( map.get( "text/xml" ), is( "text/xml" ) );
    assertThat( map.get( "custom/xml" ), is( "*/xml" ) );
    assertThat( map.get( "text/custom" ), is( "text/*" ) );
    assertThat( map.get( "custom/custom" ), is( "*/*" ) );

  }

}
