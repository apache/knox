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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.Groups;
import org.junit.Before;
import org.junit.Test;

/**
 * Test Hadoop {@link Groups} class. Basically to make sure that the
 * interface we depend on does not change.
 *
 * @since 0.11.0
 */
public class HadoopGroupsTest {

  /**
   * Use the default group mapping
   */
  public static final String GROUP_MAPPING = "org.apache.hadoop.security.JniBasedUnixGroupsMappingWithFallback";

  /**
   * Username
   */
  private String username;

  /**
   * Configuration object needed by for hadoop classes
   */
  private Configuration hadoopConfig;

  /**
   * Hadoop Groups implementation.
   */
  private Groups hadoopGroups;

  /* create instance */
  public HadoopGroupsTest() {
    super();
  }

  @Before
  public void init() {
    username = System.getProperty("user.name");

    hadoopConfig = new Configuration(false);

    hadoopConfig.set("hadoop.security.group.mapping", GROUP_MAPPING);

    hadoopGroups = new Groups(hadoopConfig);

  }

  /*
   * Test Groups on the machine running the unit test.
   */
  @Test
  public void testLocalGroups() throws Exception {

    final List<String> groupList = hadoopGroups.getGroups(username);

    assertThat("No groups found for user " + username, !groupList.isEmpty());

  }
}
