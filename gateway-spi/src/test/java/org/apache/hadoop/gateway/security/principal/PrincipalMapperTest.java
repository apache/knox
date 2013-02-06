package org.apache.hadoop.gateway.security.principal;

import static org.junit.Assert.*;

import org.apache.hadoop.gateway.security.principal.PrincipalMapper;
import org.apache.hadoop.gateway.security.principal.PrincipalMappingException;
import org.apache.hadoop.gateway.security.principal.SimplePrincipalMapper;
import org.apache.hadoop.test.category.FastTests;
import org.apache.hadoop.test.category.UnitTests;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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