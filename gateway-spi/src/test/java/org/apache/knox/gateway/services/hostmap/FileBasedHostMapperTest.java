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
package org.apache.knox.gateway.services.hostmap;

import org.apache.knox.test.TestUtils;
import org.junit.Test;

import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class FileBasedHostMapperTest {

  @Test
  public void testEverything() throws Exception {
    URL hostMapUrl = TestUtils.getResourceUrl( FileBasedHostMapperTest.class, "hostmap.txt" );
    FileBasedHostMapper mapper = new FileBasedHostMapper( hostMapUrl );

    assertThat( mapper.resolveInboundHostName( null ), nullValue() );
    assertThat( mapper.resolveOutboundHostName( null ), nullValue() );

    // external=internal
    assertThat( mapper.resolveInboundHostName( "external" ), is( "internal" ) );
    assertThat( mapper.resolveInboundHostName( "internal" ), is( "internal" ) );
    assertThat( mapper.resolveOutboundHostName( "external" ), is( "external" ) );
    assertThat( mapper.resolveOutboundHostName( "internal" ), is( "external" ) );

    // external-space = internal-space
    assertThat( mapper.resolveInboundHostName( "external-space" ), is( "internal-space" ) );
    assertThat( mapper.resolveInboundHostName( "internal-space" ), is( "internal-space" ) );
    assertThat( mapper.resolveOutboundHostName( "external-space" ), is( "external-space" ) );
    assertThat( mapper.resolveOutboundHostName( "internal-space" ), is( "external-space" ) );

//    external-list = external-list-1, external-list-2
    assertThat( mapper.resolveInboundHostName( "external-list" ), is( "external-list-1" ) );

//    internal-list-1, internal-list-2 = internal-list
    assertThat( mapper.resolveOutboundHostName( "internal-list" ), is( "internal-list-1" ) );

//    external-both-list-1, external-both-list-2 = internal-both-list-1, internal-both-list-2
    assertThat( mapper.resolveInboundHostName( "external-both-list-1" ), is( "internal-both-list-1" ) );
    assertThat( mapper.resolveInboundHostName( "external-both-list-2" ), is( "internal-both-list-1" ) );
    assertThat( mapper.resolveOutboundHostName( "internal-both-list-1" ), is( "external-both-list-1" ) );
    assertThat( mapper.resolveOutboundHostName( "internal-both-list-2" ), is( "external-both-list-1" ) );
  }

}
