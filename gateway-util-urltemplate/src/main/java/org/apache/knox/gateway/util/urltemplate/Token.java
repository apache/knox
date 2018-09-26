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
package org.apache.knox.gateway.util.urltemplate;

final class Token {

  String parameterName = null;
  String originalPattern = null;
  String effectivePattern = null;
  boolean literal = false;

  Token( String parameterName, String originalPattern, String effectivePattern, boolean literal ) {
    this.parameterName = parameterName;
    this.originalPattern = originalPattern;
    this.effectivePattern = effectivePattern;
    this.literal = literal;
  }

  Token( String parameterName, String originalPattern, boolean literal ) {
    this( parameterName, originalPattern, originalPattern, literal );
  }

  public String getParameterName() {
    return parameterName;
  }

  public String getOriginalPattern() {
    return originalPattern;
  }

  public String getEffectivePattern() {
    return effectivePattern;
  }

  public boolean isLiteral() {
    return literal;
  }

}

