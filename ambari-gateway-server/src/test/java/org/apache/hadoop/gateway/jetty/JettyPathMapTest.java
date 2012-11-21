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
package org.apache.hadoop.gateway.jetty;

import org.apache.hadoop.test.category.UnitTests;
import org.apache.hadoop.test.category.FastTests;
import org.eclipse.jetty.http.PathMap;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 *
 */
@Category( { UnitTests.class, FastTests.class } )
public class JettyPathMapTest {

  @Ignore( "This doesn't work like I expected." )
  @Test
  public void testPathMatching() {
    PathMap map = new PathMap();
    map.put( "/webhdfs", "/webhdfs" );
    map.put( "/webhdfs/dfshealth.jsp", "/webhdfs/dfshealth.jsp" );
    map.put( "/webhdfs/*", "/webhdfs/*" );

    assertThat( (String)map.match( "/webhdfs" ), equalTo( "/webhdfs" ) );
    assertThat( (String)map.match( "/webhdfs/dfshealth.jsp" ), equalTo( "/webhdfs/dfshealth.jsp" ) );
    assertThat( (String)map.match( "/webhdfs/v1" ), equalTo( "/webhdfs/*" ) );
  }

}
