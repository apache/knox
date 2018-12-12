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
package org.apache.knox.gateway.config;

import org.apache.knox.gateway.config.impl.MappedConfigurationBinding;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class FuncTest {

  public static class TestBean {
    @Configure
    String stringMember = "stringDefault";

    @Configure
    int intMember = 1;

    @Configure
    Integer integerMember = 1;

    @Configure
    public void setStringProp( String s ) {
      stringPropField = s;
    }
    protected String stringPropField = "stringDefault";

    @Configure
    @Alias("altStringProp")
    public void setNamedStringProp( String s ) {
      stringPropFieldAlt = s;
    }
    protected String stringPropFieldAlt = "stringDefault";

    @Configure
    public void setNamedArgMethod( @Configure @Alias("altArgStringProp") String s ) {
      stringPropFieldAltArg = s;
    }
    protected String stringPropFieldAltArg = "stringDefault";

    @Configure
    public void setMultiArgs(
        @Configure @Alias("multiArg1") String s,
        @Configure @Alias("multiArg2") Integer i,
        @Configure @Alias("multiArg3") int n ) {
      multiArgStringField = s;
      multiArgIntegerField = i;
      multiArgIntField = n;
    }
    String multiArgStringField = "default";
    Integer multiArgIntegerField = 0;
    int multiArgIntField;

  }

  @Test
  public void testMapOfStrings() {

    Map<String,String> testConfig = new HashMap<>();
    testConfig.put( "stringMember", "stringValue" );
    testConfig.put( "intMember", "2" );
    testConfig.put( "integerMember", "2" );
    testConfig.put( "stringProp", "stringValue" );
    testConfig.put( "altStringProp", "stringValue" );
    testConfig.put( "altArgStringProp", "stringValue" );
    testConfig.put( "multiArg1", "stringValue" );
    testConfig.put( "multiArg2", "42" );
    testConfig.put( "multiArg3", "42" );

    TestBean testBean = new TestBean();

    ConfigurationInjectorBuilder.configuration().target( testBean ).source( testConfig ).inject();

    assertThat( testBean.stringMember, is( "stringValue" ) );
    assertThat( testBean.intMember, is( 2 ) );
    assertThat( testBean.integerMember, is(2) );
    assertThat( testBean.stringPropField, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAlt, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAltArg, is( "stringValue" ) );
    assertThat( testBean.multiArgStringField, is( "stringValue" ) );
    assertThat( testBean.multiArgIntegerField, is( 42 ) );
    assertThat( testBean.multiArgIntField, is( 42 ) );
  }

  @Test
  public void testProperties() {

    Properties testConfig = new Properties();
    testConfig.put( "stringMember", "stringValue" );
    testConfig.put( "intMember", "2" );
    testConfig.put( "integerMember", "2" );
    testConfig.put( "stringProp", "stringValue" );
    testConfig.put( "altStringProp", "stringValue" );
    testConfig.put( "altArgStringProp", "stringValue" );
    testConfig.put( "multiArg1", "stringValue" );
    testConfig.put( "multiArg2", "42" );
    testConfig.put( "multiArg3", "42" );

    TestBean testBean = new TestBean();

    ConfigurationInjectorBuilder.configuration().target( testBean ).source( testConfig ).inject();

    assertThat( testBean.stringMember, is( "stringValue" ) );
    assertThat( testBean.intMember, is( 2 ) );
    assertThat( testBean.integerMember, is(2) );
    assertThat( testBean.stringPropField, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAlt, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAltArg, is( "stringValue" ) );
    assertThat( testBean.multiArgStringField, is( "stringValue" ) );
    assertThat( testBean.multiArgIntegerField, is( 42 ) );
    assertThat( testBean.multiArgIntField, is( 42 ) );
  }

  public static class TestAdapter implements ConfigurationAdapter {

    private Map<String,String> config;

    public TestAdapter( Map<String,String> config ) {
      this.config = config;
    }

    @Override
    public String getConfigurationValue( String name ) {
      return config.get( name );
    }

  }

  @Test
  public void testExplicitProvider() {

    Map<String,String> testConfig = new HashMap<>();
    testConfig.put( "stringMember", "stringValue" );
    testConfig.put( "intMember", "2" );
    testConfig.put( "integerMember", "2" );
    testConfig.put( "stringProp", "stringValue" );
    testConfig.put( "altStringProp", "stringValue" );
    testConfig.put( "altArgStringProp", "stringValue" );
    testConfig.put( "multiArg1", "stringValue" );
    testConfig.put( "multiArg2", "42" );
    testConfig.put( "multiArg3", "42" );

    TestBean testBean = new TestBean();

    ConfigurationInjectorBuilder.configuration().target( testBean ).source( new TestAdapter( testConfig ) ).inject();

    assertThat( testBean.stringMember, is( "stringValue" ) );
    assertThat( testBean.intMember, is( 2 ) );
    assertThat( testBean.integerMember, is(2) );
    assertThat( testBean.stringPropField, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAlt, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAltArg, is( "stringValue" ) );
    assertThat( testBean.multiArgStringField, is( "stringValue" ) );
    assertThat( testBean.multiArgIntegerField, is( 42 ) );
    assertThat( testBean.multiArgIntField, is( 42 ) );
  }

  @Test
  public void testMapOfObjects() {

    Map<Object,Object> testConfig = new HashMap<>();
    testConfig.put( "stringMember", "stringValue" );
    testConfig.put( "intMember", 42 );
    testConfig.put( "integerMember", 42);
    testConfig.put( "stringProp", "stringValue" );
    testConfig.put( "altStringProp", "stringValue" );
    testConfig.put( "altArgStringProp", "stringValue" );
    testConfig.put( "multiArg1", "stringValue" );
    testConfig.put( "multiArg2", 42);
    testConfig.put( "multiArg3", "42" );

    TestBean testBean = new TestBean();

    ConfigurationInjectorBuilder.configuration().target( testBean ).source( testConfig ).inject();

    assertThat( testBean.stringMember, is( "stringValue" ) );
    assertThat( testBean.intMember, is( 42 ) );
    assertThat( testBean.integerMember, is(42) );
    assertThat( testBean.stringPropField, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAlt, is( "stringValue" ) );
    assertThat( testBean.stringPropFieldAltArg, is( "stringValue" ) );
    assertThat( testBean.multiArgStringField, is( "stringValue" ) );
    assertThat( testBean.multiArgIntegerField, is( 42 ) );
    assertThat( testBean.multiArgIntField, is( 42 ) );
  }

  public class Target {
    @Configure @Alias("user.name")
    private String user;
  }

  public class Adapter implements ConfigurationAdapter {
    @Override
    public Object getConfigurationValue( String name ) throws ConfigurationException {
      return System.getProperty( name );
    }
  }

  @Test
  public void testFactoryConfigurationDirect() {
    Target target = new Target();
    ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
    assertThat( target.user, is( System.getProperty( "user.name" ) ) );
  }

  @Test
  public void testFactoryConfigurationAdapter() {
    Target target = new Target();
    ConfigurationInjectorBuilder.configuration().target( target ).source( new Adapter() ).inject();
    assertThat( target.user, is( System.getProperty( "user.name" ) ) );
  }

  @Test
  public void testMissingRequiredFieldConfiguration() {
    class RequiredFieldTarget {
      @SuppressWarnings("unused")
      @Configure
      private String required;
    }
    RequiredFieldTarget target = new RequiredFieldTarget();
    try {
      ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
      fail( "Expected an exception because the configuration values could not be populated." );
    } catch ( ConfigurationException e ) {
      assertThat( e.getMessage(), allOf(containsString("Failed"),containsString( "find" ),containsString( "required" )) );
    }
  }

  @Test
  public void testMissingOptionalFieldConfiguration() {
    class OptionalFieldTarget {
      @Configure
      @Optional
      private String optional = "default";
    }
    OptionalFieldTarget target = new OptionalFieldTarget();
    ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
    assertThat( target.optional, is("default") );
  }

  @Test
  public void testMissingRequiredConfigurationParameter() {
    class Target {
      private String field;
      @Configure
      public void setRequired(String value) {
        field = value;
      }
    }
    Target target = new Target();
    try {
      ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
      fail( "Expected an exception because the configuration values could not be populated." );
    } catch ( ConfigurationException e ) {
      assertThat( e.getMessage(), allOf(containsString("Failed"),containsString( "find" ),containsString( "required" )) );
    }
  }

  @Test
  public void testMissingRequiredConfigurationParameterWithDefault() {
    class Target {
      private String field;
      @Configure
      public void setRequired(@Default("default")String value) {
        field = value;
      }
    }
    Target target = new Target();
    ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
    assertThat( target.field, is( "default" ) );
  }

  @Test
  public void testTwoMissingRequiredConfigurationParameterWithDefault() {
    class Target {
      private String field1;
      private String field2;
      @Configure
      public void setRequired(@Default("default1")String value1, @Default("default2")String value2) {
        field1 = value1;
        field2 = value2;
      }
    }
    Target target = new Target();
    ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
    assertThat( target.field1, is( "default1" ) );
    assertThat( target.field2, is("default2") );
  }

  @Test
  public void testFieldBinding() {
    class Target {
      @Configure
      private String user;
    }
    class Binding extends MappedConfigurationBinding {
      Binding() {
        bind("user","user.name");
      }
    }
    Target target = new Target();
    Properties source = System.getProperties();
    ConfigurationBinding binding = new Binding();
    ConfigurationInjectorBuilder.configuration().target( target ).source( source ).binding( binding ).inject();
    assertThat( target.user, is(System.getProperty("user.name")));

  }

  @Test
  public void testFieldBindingUsingBuilderBinding() {
    class Target {
      @Configure
      private String user;
    }
    Target target = new Target();
    Properties source = System.getProperties();
    ConfigurationInjectorBuilder.configuration().target( target ).source( source ).bind( "user", "user.name" ).inject();
    assertThat( target.user, is(System.getProperty("user.name")));

  }

  @Test
  public void testFieldBindingUsingBuilderBindingFactory() {
    class Target {
      @Configure
      private String user;
    }
    Target target = new Target();
    Properties source = System.getProperties();
    ConfigurationBinding binding = ConfigurationInjectorBuilder
        .configuration().bind( "user", "user.name" ).binding();
    ConfigurationInjectorBuilder.configuration().target( target ).source( source ).binding( binding ).inject();
    assertThat( target.user, is( System.getProperty( "user.name" ) ) );

  }

  public static class UserBean {
    public String getPrincipal() {
      return "test-user";
    }
  }

  @Test
  public void testBeanAdapter() {
    Target target = new Target();
    UserBean bean = new UserBean();
    ConfigurationInjectorBuilder.configuration()
        .target( target )
        .source( bean )
        .bind( "user.name", "principal" )
        .inject();
    assertThat( target.user, is( "test-user" ) );

  }

}
