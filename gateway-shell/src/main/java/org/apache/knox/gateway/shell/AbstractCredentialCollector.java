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

import java.nio.charset.StandardCharsets;

public abstract class AbstractCredentialCollector implements CredentialCollector {

  protected String prompt;
  protected String value;
  private String name;

  public AbstractCredentialCollector() {
    super();
  }

  public boolean validate() {
    return true;
  }

  @Override
  public String string() {
    return value;
  }

  @Override
  public char[] chars() {
    return value.toCharArray();
  }

  @Override
  public byte[] bytes() {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String name() {
    return name;
  }

}