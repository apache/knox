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
package org.apache.hadoop.gateway.security.principal;

import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
*
*/
@Category( { UnitTests.class, FastTests.class } )
public class PrincipalMapperTest {
  PrincipalMapper mapper;

  @Before
  public void setup() {
    mapper = new SimplePrincipalMapper();
  }
  
  @Test
  public void testNonNullSimplePrincipalMapping() {
    String principalMapping = "lmccay,kminder=hdfs;newuser=mapred";
    try {
      mapper.loadMappingTable(principalMapping);
    }
    catch (PrincipalMappingException pme) {
      fail();
    }
    
    assertTrue(mapper.mapPrincipal("lmccay").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("kminder").equals("hdfs"));
    
    assertTrue(mapper.mapPrincipal("newuser").equals("mapred"));

    assertTrue(mapper.mapPrincipal("hdfs").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("mapred").equals("mapred"));

    assertTrue(mapper.mapPrincipal("stink").equals("stink"));
  }

  @Test
  public void testNonNullEndingSemiColonSimplePrincipalMapping() {
    String principalMapping = "lmccay,kminder=hdfs;newuser=mapred;";
    try {
      mapper.loadMappingTable(principalMapping);
    }
    catch (PrincipalMappingException pme) {
      fail();
    }
    
    assertTrue(mapper.mapPrincipal("lmccay").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("kminder").equals("hdfs"));
    
    assertTrue(mapper.mapPrincipal("newuser").equals("mapred"));

    assertTrue(mapper.mapPrincipal("hdfs").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("mapred").equals("mapred"));

    assertTrue(mapper.mapPrincipal("stink").equals("stink"));
  }

  @Test
  public void testNullSimplePrincipalMapping() {
    String principalMapping = null;
    try {
      mapper.loadMappingTable(principalMapping);
    }
    catch (PrincipalMappingException pme) {
      fail();
    }
    
    assertTrue(mapper.mapPrincipal("lmccay").equals("lmccay"));
    assertTrue(mapper.mapPrincipal("kminder").equals("kminder"));
    
    assertTrue(mapper.mapPrincipal("newuser").equals("newuser"));

    assertTrue(mapper.mapPrincipal("hdfs").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("mapred").equals("mapred"));

    assertTrue(mapper.mapPrincipal("stink").equals("stink"));
  }

  @Test
  public void testInvalidSimplePrincipalMapping() {
    String principalMapping = "ksdlhfjkdshf;kjdshf";
    try {
      mapper.loadMappingTable(principalMapping);
    }
    catch (PrincipalMappingException pme) {
      // expected
    }
    
    assertTrue(mapper.mapPrincipal("lmccay").equals("lmccay"));
    assertTrue(mapper.mapPrincipal("kminder").equals("kminder"));
    
    assertTrue(mapper.mapPrincipal("newuser").equals("newuser"));

    assertTrue(mapper.mapPrincipal("hdfs").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("mapred").equals("mapred"));

    assertTrue(mapper.mapPrincipal("stink").equals("stink"));
  }

  @Test
  public void testPartiallyInvalidSimplePrincipalMapping() {
    String principalMapping = "lmccay=hdfs;kjdshf";
    try {
      mapper.loadMappingTable(principalMapping);
    }
    catch (PrincipalMappingException pme) {
      // expected
    }
    
    assertTrue(mapper.mapPrincipal("lmccay").equals("lmccay"));
    assertTrue(mapper.mapPrincipal("kminder").equals("kminder"));
    
    assertTrue(mapper.mapPrincipal("newuser").equals("newuser"));

    assertTrue(mapper.mapPrincipal("hdfs").equals("hdfs"));
    assertTrue(mapper.mapPrincipal("mapred").equals("mapred"));

    assertTrue(mapper.mapPrincipal("stink").equals("stink"));
  }
}