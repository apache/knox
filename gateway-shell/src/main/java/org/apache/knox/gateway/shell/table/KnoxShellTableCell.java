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

import java.util.List;

class KnoxShellTableCell {
  int rowIndex;
  int colIndex;
  String header;
  String value;

  KnoxShellTableCell(List<String> headers, List<List<String>> rows, int colIndex, int rowIndex) {
    this.rowIndex = rowIndex;
    this.colIndex = colIndex;
    if (!headers.isEmpty()) {
      this.header = headers.get(colIndex);
    }
    if (!rows.isEmpty()) {
      this.value = rows.get(rowIndex).get(colIndex);
    }
  }

  KnoxShellTableCell(List<String> headers, List<List<String>> rows, String name, int rowIndex) {
    this.rowIndex = rowIndex;
    if (!headers.isEmpty()) {
      this.header = name;
      this.colIndex = headers.indexOf(name);
    }
    if (!rows.isEmpty()) {
      this.value = rows.get(rowIndex).get(colIndex);
    }
  }

  KnoxShellTableCell value(String value) {
    this.value = value;
    return this;
  }

  KnoxShellTableCell header(String name) {
    this.header = name;
    return this;
  }

}
