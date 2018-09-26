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
package org.apache.knox.gateway.jetty;

import org.eclipse.jetty.http.PathMap;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 *
 */
//@Category( { UnitTests.class, FastTests.class } )
public class JettyPathMapTest {

  //@Ignore( "This doesn't work like I expected." )
  //@Test
  public void testPathMatching() {
    PathMap map;

    map = new PathMap();
    map.put( "/path", "/path" );
    assertThat( (String)map.match("/path"), is("/path") );

    map = new PathMap();
    map.put( "/path", "/path" );
    map.put( "/path/", "/path/" );
    assertThat( (String)map.match("/path"), is("/path") );
    assertThat( (String)map.match("/path/"), is("/path/") );

    map = new PathMap();
    map.put( "/path/*", "/path/*" );
    map.put( "/path", "/path" );
    map.put( "/path/", "/path/" );
    assertThat( (String)map.match("/path"), is("/path") );
    assertThat( (String)map.match("/path/"), is("/path/") );
    assertThat( (String)map.match("/path/sub"), is("/path/*") );

    map = new PathMap();
    map.put( "/path", "/path" );
    map.put( "/path/", "/path/" );
    map.put( "/path/*", "/path/*" );
    assertThat( (String)map.match( "/path/sub" ), is("/path/*") );

    // Here the addition of the * path "overwrites" the exact matches.
    // Above this worked if the /path and /path/ were added after /path/*.
    assertThat( (String)map.match("/path"), is("/path") );
    assertThat( (String)map.match("/path/"), is("/path/") );

  }

}
