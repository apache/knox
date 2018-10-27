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
package org.apache.knox.gateway.shell.hbase.table;

import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.hbase.table.row.Row;
import org.apache.knox.gateway.shell.hbase.table.scanner.Scanner;

public class Table {

  private String name;
  private KnoxSession session;

  public Table( String name ) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  public Table session( KnoxSession session ) {
    this.session = session;
    return this;
  }

  public KnoxSession session() {
    return session;
  }

  public TableList.Request list() {
    return new TableList.Request( session );
  }

  public TableSchema.Request schema() {
    return new TableSchema.Request( session, name );
  }

  public CreateTable.Request create() {
    return new CreateTable.Request( session, name );
  }

  public UpdateTable.Request update() {
    return new UpdateTable.Request( session, name );
  }

  public TableRegions.Request regions() {
    return new TableRegions.Request( session, name );
  }

  public DeleteTable.Request delete() {
    return new DeleteTable.Request( session, name );
  }

  public TruncateTable.Request truncate() {
    return new TruncateTable.Request( session, name );
  }

  public Row row( String id ) {
    return new Row( id ).table( this );
  }

  public Row row() {
    return row( null );
  }

  public Scanner scanner( String id ) {
    return new Scanner( id ).table( this );
  }

  public Scanner scanner() {
    return scanner( null );
  }
}
