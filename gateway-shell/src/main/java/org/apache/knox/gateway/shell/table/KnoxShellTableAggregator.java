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

public class KnoxShellTableAggregator {
  private KnoxShellTable tableToAggregate;
  private String[] cols;

  public KnoxShellTableAggregator(KnoxShellTable table) {
    tableToAggregate = table;
  }

  public KnoxShellTableAggregator columns(String cols) {
    this.cols = cols.split("\\s*,\\s*");
    return this;
  }

  public KnoxShellTable functions(String funcs) {
    String[] functions = funcs.split("\\s*,\\s*");

    KnoxShellTable table = new KnoxShellTable();
    table.header("");
    for (String col : cols) {
      table.header(col);
    }

    for (String func : functions) {
      table.row();
      table.value(func);
      for (String col : cols) {
        table.value(executeFunction(col, func));
      }
    }
    return table;
  }

  private Double executeFunction(String col, String func) {
    Double value = 0.0d;
    if ("min".equalsIgnoreCase(func)) {
      value = tableToAggregate.min(col);
    }
    else if ("max".equalsIgnoreCase(func)) {
      value = tableToAggregate.max(col);
    }
    else if ("mean".equalsIgnoreCase(func)) {
      value = tableToAggregate.mean(col);
    }
    else if ("mode".equalsIgnoreCase(func)) {
      value = tableToAggregate.mode(col);
    }
    else if ("median".equalsIgnoreCase(func)) {
      value = tableToAggregate.median(col);
    }
    else if ("sum".equalsIgnoreCase(func)) {
      value = tableToAggregate.sum(col);
    }
    return value;
  }
}
