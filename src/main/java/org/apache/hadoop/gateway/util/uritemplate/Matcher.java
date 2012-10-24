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
package org.apache.hadoop.gateway.util.uritemplate;

import java.net.URI;

public class Matcher<T> {

  public Match match( Template template, URI uri ) {
    return null;
  }

  public void add( Template template, T value ) {
  }

  public class Match<T> {

    private Template template;
    private T value;

    private Match( Template template, T value ) {
      this.template = template;
      this.value = value;
    }

    public Template getTemplate() {
      return template;
    }

    public T getValue() {
      return value;
    }
  }

  private class Node {

  }

}
