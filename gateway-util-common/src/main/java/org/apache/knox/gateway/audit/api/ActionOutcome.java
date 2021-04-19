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

/**
 * Contains a list of possible action outcomes. It may be used to keep audit
 * records consistent across services and components. For example, to avoid the
 * following: "Success", "success", "SUCCESS", "Succeed" Action outcomes doesn't
 * restricted to this list and any constants from component's source code may be
 * used.
 */
public abstract class ActionOutcome {
  private ActionOutcome() {
  }

  public static final String SUCCESS = "success";
  public static final String FAILURE = "failure";
  public static final String UNAVAILABLE = "unavailable";
  public static final String WARNING = "warning";

}
