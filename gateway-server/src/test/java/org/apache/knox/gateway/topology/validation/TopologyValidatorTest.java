/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.validation;

import java.net.URL;

import org.apache.knox.test.TestUtils;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class TopologyValidatorTest {

  @Test
  public void testValidateApplications() throws Exception {
    URL url;
    TopologyValidator validator;

    url = TestUtils.getResourceUrl( TopologyValidatorTest.class, "topology-valid-complete.xml" );
    validator = new TopologyValidator( url );
    assertThat( validator.getErrorString(), validator.validateTopology(), is( true ) );

    url = TestUtils.getResourceUrl( TopologyValidatorTest.class, "topology-valid.xml" );
    validator = new TopologyValidator( url );
    assertThat( validator.getErrorString(), validator.validateTopology(), is( true ) );

    url = TestUtils.getResourceUrl( TopologyValidatorTest.class, "topology-valid-with-name.xml" );
    validator = new TopologyValidator( url );
    assertThat( validator.getErrorString(), validator.validateTopology(), is( true ) );

  }

}