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
package org.apache.knox.gateway.grpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Coarse authorization by method name, which needs no schema at all: gRPC puts
 * the method in the request path, so this works on a purely byte-level proxy.
 */
public class MethodAccessPolicyTest {

  private static final String ADD_ARTIFACTS = "spark.connect.SparkConnectService/AddArtifacts";
  private static final String EXECUTE_PLAN = "spark.connect.SparkConnectService/ExecutePlan";

  @Test
  public void permitsEverythingWhenUnconfigured() {
    assertTrue(MethodAccessPolicy.allowAll().isUnrestricted());
    assertTrue(MethodAccessPolicy.allowAll().isPermitted(ADD_ARTIFACTS));
    assertTrue(MethodAccessPolicy.of(null, null).isPermitted(ADD_ARTIFACTS));
    assertTrue(MethodAccessPolicy.of("", "  ").isPermitted(ADD_ARTIFACTS));
  }

  @Test
  public void deniesByBareMethodName() {
    // The form an operator would most naturally write.
    final MethodAccessPolicy policy = MethodAccessPolicy.of("AddArtifacts", null);
    assertFalse(policy.isPermitted(ADD_ARTIFACTS));
    assertTrue(policy.isPermitted(EXECUTE_PLAN));
  }

  @Test
  public void deniesByFullyQualifiedName() {
    final MethodAccessPolicy policy = MethodAccessPolicy.of(ADD_ARTIFACTS, null);
    assertFalse(policy.isPermitted(ADD_ARTIFACTS));
    assertTrue(policy.isPermitted(EXECUTE_PLAN));
  }

  @Test
  public void matchingIsCaseInsensitive() {
    final MethodAccessPolicy policy = MethodAccessPolicy.of("addartifacts", null);
    assertFalse(policy.isPermitted(ADD_ARTIFACTS));
  }

  @Test
  public void deniesSeveralMethods() {
    final MethodAccessPolicy policy = MethodAccessPolicy.of("AddArtifacts, ArtifactStatus", null);
    assertFalse(policy.isPermitted(ADD_ARTIFACTS));
    assertFalse(policy.isPermitted("spark.connect.SparkConnectService/ArtifactStatus"));
    assertTrue(policy.isPermitted(EXECUTE_PLAN));
  }

  @Test
  public void anAllowListIsExhaustive() {
    // An RPC added by a newer protocol version must not appear by default just
    // because nobody thought to deny it.
    final MethodAccessPolicy policy = MethodAccessPolicy.of(null, "ExecutePlan, AnalyzePlan");
    assertTrue(policy.isPermitted(EXECUTE_PLAN));
    assertTrue(policy.isPermitted("spark.connect.SparkConnectService/AnalyzePlan"));
    assertFalse(policy.isPermitted(ADD_ARTIFACTS));
    assertFalse(policy.isPermitted("spark.connect.SparkConnectService/SomeRpcFromTheFuture"));
  }

  @Test
  public void denyBeatsAllow() {
    final MethodAccessPolicy policy =
        MethodAccessPolicy.of("AddArtifacts", "AddArtifacts, ExecutePlan");
    assertFalse("an explicit denial should win", policy.isPermitted(ADD_ARTIFACTS));
    assertTrue(policy.isPermitted(EXECUTE_PLAN));
  }

  @Test
  public void handlesAMethodNameWithoutAService() {
    final MethodAccessPolicy policy = MethodAccessPolicy.of("AddArtifacts", null);
    assertFalse(policy.isPermitted("AddArtifacts"));
    assertTrue(policy.isPermitted("SomethingElse"));
  }

  @Test
  public void aConfiguredPolicyIsNotUnrestricted() {
    assertFalse(MethodAccessPolicy.of("AddArtifacts", null).isUnrestricted());
    assertFalse(MethodAccessPolicy.of(null, "ExecutePlan").isUnrestricted());
  }
}
