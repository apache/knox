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
package org.apache.knox.gateway.shell.hbase.table.scanner;

import org.apache.knox.gateway.shell.hbase.table.Table;

public class Scanner {

  private String id;
  private Table table;

  public Scanner( String id ) {
    this.id = id;
  }

  public Scanner() {
    this( null );
  }

  public Scanner table( Table table ) {
    this.table = table;
    return this;
  }

  public CreateScanner.Request create() {
    return new CreateScanner.Request( table.session(), table.name() );
  }

  public ScannerGetNext.Request getNext() {
    return new ScannerGetNext.Request( table.session(), id, table.name() );
  }

  public DeleteScanner.Request delete() {
    return new DeleteScanner.Request( table.session(), id, table.name() );
  }
}
