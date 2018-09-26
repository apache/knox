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

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class PropertiesMethodSampleTest {

  public static class Target {

    private String user = "nobody";
    private String home = "nowhere";
    private String temp = "nowhere";

    @Configure
    @Alias("user.name")
    public void setUser( String value ) {
      user = value;
    }

    @Configure
    public void setDirs(
        @Alias("user.dir") String home,
        @Alias("java.io.tmpdir") String temp ) {
      this.home = home;
      this.temp = temp;
    }
  }

  @Test
  public void sample() {
    Target target = new Target();
    ConfigurationInjectorBuilder.configuration().target( target ).source( System.getProperties() ).inject();
    assertThat( target.user, is( System.getProperty( "user.name" ) ) );
    assertThat( target.home, is( System.getProperty( "user.dir" ) ) );
    assertThat( target.temp, is( System.getProperty( "java.io.tmpdir" ) ) );
  }

}
