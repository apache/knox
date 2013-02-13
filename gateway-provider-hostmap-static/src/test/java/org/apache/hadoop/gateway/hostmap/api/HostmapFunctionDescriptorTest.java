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
package org.apache.hadoop.gateway.hostmap.api;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HostmapFunctionDescriptorTest {

  @Test
  public void testGetAndSet() {
    HostmapFunctionDescriptor descriptor = new HostmapFunctionDescriptor();

    assertThat( descriptor.name(), is( "hostmap" ) );
    assertThat( descriptor.config(), nullValue() );

    // Test Fluent API
    descriptor.config( "test-config-location-fluent" );
    assertThat( descriptor.config(), is( "test-config-location-fluent" ) );
    assertThat( descriptor.getConfig(), is( "test-config-location-fluent" ) );

    // Test Bean API
    descriptor.setConfig( "test-config-location-bean" );
    assertThat( descriptor.config(), is( "test-config-location-bean" ) );
    assertThat( descriptor.getConfig(), is( "test-config-location-bean" ) );
  }

}
