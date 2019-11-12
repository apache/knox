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
import java.util.Iterator;
import java.util.List;

public class JoinKnoxShellTableBuilder extends KnoxShellTableBuilder {

  private KnoxShellTable left;
  private KnoxShellTable right;

  JoinKnoxShellTableBuilder(KnoxShellTable table) {
    super(table);
  }

  @Override
  public JoinKnoxShellTableBuilder title(String title) {
    this.title = title;
    return this;
  }

  public JoinKnoxShellTableBuilder left(KnoxShellTable left) {
    this.left = left;
    return this;
  }

  public JoinKnoxShellTableBuilder right(KnoxShellTable right) {
    this.right = right;
    return this;
  }

  public KnoxShellTable on(String columnName) {
    final int leftIndex = left.headers.indexOf(columnName);
    final int rightIndex = right.headers.indexOf(columnName);
    return on(leftIndex, rightIndex);
  }

  public KnoxShellTable on(String columnName, String columnName2) {
      final int leftIndex = left.headers.indexOf(columnName);
      final int rightIndex = right.headers.indexOf(columnName2);
      return on(leftIndex, rightIndex);
    }

  public KnoxShellTable on(int leftIndex, int rightIndex) {
    if (title != null) {
      this.table.title(title);
    }

    this.table.headers.addAll(new ArrayList<>(left.headers));
    for (List<Comparable<? extends Object>> row : left.rows) {
      this.table.rows.add(new ArrayList<>(row));
    }
    List<Comparable<? extends Object>> row;
    Comparable<? extends Object> leftKey;
    int matchedIndex;

    this.table.headers.addAll(new ArrayList<>(right.headers));
    for (Iterator<List<Comparable<? extends Object>>> it = this.table.rows.iterator(); it.hasNext();) {
      row = it.next();
      leftKey = row.get(leftIndex);
      if (leftKey != null) {
        matchedIndex = right.values(rightIndex).indexOf(leftKey);
        if (matchedIndex > -1) {
          row.addAll(right.rows.get(matchedIndex));
        } else {
          it.remove();
        }
      }
    }
    return this.table;
  }

}
