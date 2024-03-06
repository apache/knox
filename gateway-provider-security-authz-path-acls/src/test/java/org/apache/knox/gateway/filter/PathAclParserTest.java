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
package org.apache.knox.gateway.filter;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test paring of path ACLs rules
 */
@Ignore
public class PathAclParserTest {
  final Map<String, String> rawRules = new HashMap<>();

  @Before
  public void setUp() {
    rawRules.put("rule_user", "/foo/user/*;hdfs,admin;*;*");
    rawRules.put("rule_group", "/foo/groups/*;*;admin-group;*");
    rawRules.put("rule_ip", "/foo/groups/*;*;*;67.54.12.11");
    rawRules.put("rule_allow_all", "/foo/groups/*;*;*;*");
    rawRules.put("rule_deny_all", "/foo/groups/*; ; ; ;");
  }

  @Test
  public void testParseACLs() {
    PathAclParser pathAclParser = new PathAclParser();
    pathAclParser.parsePathAcls("test", rawRules);
    Map rulesMap = pathAclParser.getRulesMap();
    assertNotNull("Rules map should not be null", rulesMap);
    assertEquals(5, rulesMap.size());
  }

  @Test(expected = InvalidACLException.class)
  public void testInvalidACLs() {
    final Map<String, String> rawRules1 = new HashMap<>();
    rawRules1.put("rule_user", "/foo/user/*;hdfs,admin;*");

    PathAclParser pathAclParser = new PathAclParser();
    pathAclParser.parsePathAcls("test", rawRules1);
  }
}
