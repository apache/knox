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
package org.apache.knox.gateway.audit.api;

public abstract class Action {
  private Action() {
  }

  public static final String AUTHENTICATION = "authentication";
  public static final String AUTHORIZATION = "authorization";
  public static final String REDEPLOY = "redeploy";
  public static final String DEPLOY = "deploy";
  public static final String LOAD = "load";
  public static final String UNDEPLOY = "undeploy";
  public static final String IDENTITY_MAPPING = "identity-mapping";
  public static final String DISPATCH = "dispatch";
  public static final String ACCESS = "access";

}
