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
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class KnoxShellTableFilter {

  private KnoxShellTable tableToFilter;
  private int index;

  public KnoxShellTableFilter table(KnoxShellTable table) {
    this.tableToFilter = table;
    return this;
  }

  public KnoxShellTableFilter name(String name) {
    index = tableToFilter == null ? -1 : tableToFilter.headers.indexOf(name);
    return this;
  }

  public KnoxShellTableFilter index(int index) {
    this.index = index;
    return this;
  }

  //TODO: use Predicate to evaluate the Pattern.matches 
  // for regular expressions: startsWith, endsWith, contains,
  // doesn't contain, etc
  public KnoxShellTable regex(String regex) {
    final Pattern pattern = Pattern.compile(regex);
    final KnoxShellTable filteredTable = new KnoxShellTable();
    filteredTable.headers.addAll(tableToFilter.headers);
    for (List<Comparable<?>> row : tableToFilter.rows) {
      if (pattern.matcher(row.get(index).toString()).matches()) {
        filteredTable.row();
        row.forEach(value -> {
          filteredTable.value(value);
        });
      }
    }
    return filteredTable;
  }

  @SuppressWarnings("rawtypes")
  private KnoxShellTable filter(Predicate<Comparable> p) throws KnoxShellTableFilterException {
    try {
    final KnoxShellTable filteredTable = new KnoxShellTable();
    filteredTable.headers.addAll(tableToFilter.headers);
    for (List<Comparable<? extends Object>> row : tableToFilter.rows) {
      if (p.test(row.get(index))) {
        filteredTable.row(); // Adds a new empty row to filtered table
        // Add each value to the row
        row.forEach(value -> {
          filteredTable.value(value);
        });
      }
    }
    return filteredTable;
    } catch (Exception e) {
      throw new KnoxShellTableFilterException(e);
    }
  }

  @SuppressWarnings("unchecked")
  public KnoxShellTable greaterThan(Comparable<? extends Object> comparable) throws KnoxShellTableFilterException {
    return filter(s -> s.compareTo(comparable) > 0);
  }

  @SuppressWarnings("unchecked")
  public KnoxShellTable lessThan(Comparable<? extends Object> comparable) throws KnoxShellTableFilterException {
    return filter(s -> s.compareTo(comparable) < 0);
  }

  @SuppressWarnings("unchecked")
  public KnoxShellTable greaterThanOrEqualTo(Comparable<? extends Object> comparable) throws KnoxShellTableFilterException {
    return filter(s -> s.compareTo(comparable) > 0 || s.compareTo(comparable) == 0);
  }

  @SuppressWarnings("unchecked")
  public KnoxShellTable lessThanOrEqualTo(Comparable<? extends Object> comparable) throws KnoxShellTableFilterException {
    return filter(s -> s.compareTo(comparable) < 0 || s.compareTo(comparable) == 0);
  }

  @SuppressWarnings("unchecked")
  public KnoxShellTable EqualTo(Comparable<? extends Object> comparable) throws KnoxShellTableFilterException {
    return filter(s -> s.compareTo(comparable) == 0);
  }
}
