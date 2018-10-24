/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.shell.hbase.table.row;

import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;

public class InsertableColumn extends Column {

  private Object value;
  private Long time;

  public InsertableColumn( String family, String qualifier, Object value, Long time ) {
    super( family, qualifier );
    this.value = value;
    this.time = time;
  }

  public Object value() {
    return value;
  }

  public Long time() {
    return time;
  }

  public String encodedName() {
    return Base64.encodeBase64String( toURIPart().getBytes( StandardCharsets.UTF_8 ) );
  }

  public String encodedValue() {
    String stringValue = value.toString();
    return Base64.encodeBase64String( stringValue.getBytes( StandardCharsets.UTF_8 ) );
  }
}
