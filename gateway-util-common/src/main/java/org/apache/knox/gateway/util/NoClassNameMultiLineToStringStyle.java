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
package org.apache.knox.gateway.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * See https://github.com/apache/commons-lang/pull/308 (at the time of this
 * class being written the PR is not merged)
 */
@SuppressWarnings("serial")
public class NoClassNameMultiLineToStringStyle extends ToStringStyle {

  public NoClassNameMultiLineToStringStyle() {
    super();
    this.setUseClassName(false);
    this.setUseIdentityHashCode(false);
    this.setContentStart(StringUtils.EMPTY);
    this.setFieldSeparator(System.lineSeparator());
    this.setFieldSeparatorAtStart(false);
    this.setContentEnd(System.lineSeparator());
  }
}
