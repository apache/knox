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
package org.apache.knox.gateway.shell.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.knox.gateway.util.NoClassNameMultiLineToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;

class KnoxShellTableCall {

  static final String KNOX_SHELL_TABLE_FILTER_TYPE = "org.apache.knox.gateway.shell.table.KnoxShellTableFilter";

  private final String invokerClass;
  private final String method;
  private boolean builderMethod;
  private final Map<Object, Class<?>> params;

  KnoxShellTableCall(String invokerClass, String method, boolean builderMethod, Map<Object, Class<?>> params) {
    this.invokerClass = invokerClass;
    this.method = method;
    this.builderMethod = builderMethod;
    this.params = params;
  }

  public String getInvokerClass() {
    return invokerClass;
  }

  public String getMethod() {
    return method;
  }

  public boolean isBuilderMethod() {
    return builderMethod;
  }

  public Map<Object, Class<?>> getParams() {
    return params == null ? Collections.emptyMap() : params;
  }

  @JsonIgnore
  boolean hasSensitiveData() {
    return "username".equals(getMethod()) || "pwd".equals(getMethod());
  }

  @JsonIgnore
  Class<?>[] getParameterTypes() {
    final List<Class<?>> parameterTypes = new ArrayList<>(params.size());
    if (KNOX_SHELL_TABLE_FILTER_TYPE.equals(invokerClass) && builderMethod) {
      parameterTypes.add(Comparable.class);
    } else {
      parameterTypes.addAll(params.values());
    }

    return parameterTypes.toArray(new Class<?>[0]);
  }

  @Override
  public int hashCode() {
    return HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(Object obj) {
    return EqualsBuilder.reflectionEquals(this, obj);
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, new NoClassNameMultiLineToStringStyle());
  }

}
