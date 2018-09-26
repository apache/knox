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

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class AdapterSampleTest {

  private static class Target {
    @Configure
    private String username = null;
  }

  private static class Adapter implements ConfigurationAdapter {
    private Map<String, Object> config;
    public Adapter( Map<String, Object> config ) {
      this.config = config;
    }
    @Override
    public Object getConfigurationValue( String name ) throws ConfigurationException {
      Object value = config.get( name.toUpperCase(Locale.ROOT) );
      return value == null ? null : value.toString();
    }
  }

  private static final Map<String, Object> CONFIG = new ConcurrentHashMap<>();
  static {
    CONFIG.put( "USERNAME", "somebody" );
  }

  @Test
  public void sample() {
    Target target = new Target();
    Adapter adapter = new Adapter( CONFIG );
    ConfigurationInjectorBuilder.configuration().target( target ).source( adapter ).inject();
    assertThat( target.username, is( "somebody" ) );
  }

}
