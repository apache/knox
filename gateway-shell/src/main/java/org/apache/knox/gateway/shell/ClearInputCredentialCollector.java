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
package org.apache.knox.gateway.shell;


public class ClearInputCredentialCollector extends AbstractJavaConsoleCredentialCollector {
  public static final String COLLECTOR_TYPE = "ClearInput";
  /* (non-Javadoc)
   * @see CredentialCollector#collect()
   */
  @Override
  public void collect() throws CredentialCollectionException {
    boolean valid = false;
    while (!valid) {
      collectClearCredential(prompt);
      valid = validate();
      if (!valid) {
        System.out.println("** Must not be empty **");
      }
    }
  }

  /* (non-Javadoc)
   * @see CredentialCollector#name()
   */
  @Override
  public String type() {
    return COLLECTOR_TYPE;
  }
}
