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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

class KnoxShellTableRenderer {

  private static int CELL_PAD_SIZE = 2;
  private static char CELL_CORNER_CHAR = '+';
  private static char CELL_WALL_CHAR = '|';
  private static char CELL_DASH_CHAR = '-';

  private final KnoxShellTable tableToRender;

  KnoxShellTableRenderer(KnoxShellTable tableToRender) {
    this.tableToRender = tableToRender;
  }

  String toCSV() {
    return toCSV(null);
  }

  String toCSV(String filePath) {
    final StringBuilder csv = new StringBuilder();
    String header;
    for (int i = 0; i < tableToRender.headers.size(); i++) {
      header = tableToRender.headers.get(i);
      csv.append(header);
      if (i < tableToRender.headers.size() - 1) {
        csv.append(',');
      } else {
        csv.append('\n');
      }
    }
    for (List<Comparable<? extends Object>> row : tableToRender.rows) {
      for (int ii = 0; ii < row.size(); ii++) {
        csv.append(row.get(ii));
        if (ii < row.size() - 1) {
          csv.append(',');
        } else {
          csv.append('\n');
        }
      }
    }
    String content = csv.toString();
    if (filePath != null) {
      try {
        KnoxShellTableFileUtils.persistToFile(filePath, content);
      } catch (IOException e) {
        System.out.println("Persistence of CSV file has failed. " + e.getMessage());
        e.printStackTrace();
      }
    }

    return content;
  }

  @Override
  public String toString() {
    if (!tableToRender.headers.isEmpty() && !tableToRender.rows.isEmpty() && tableToRender.headers.size() != tableToRender.rows.get(0).size()) {
      throw new IllegalStateException("Number of columns and headers must be the same.");
    }
    StringBuilder sb = new StringBuilder();
    Map<Integer, Integer> widthMap = getWidthMap();

    if (StringUtils.isNoneBlank(tableToRender.title)) {
      sb.append(tableToRender.title);
      newLine(sb, 1);
    }
    int colCount = 0;
    if (!tableToRender.rows.isEmpty()) {
      colCount = tableToRender.rows.get(0).size();
    }
    if (!tableToRender.headers.isEmpty()) {
      colCount = tableToRender.headers.size();
      createBorder(sb, colCount, widthMap);
      newLine(sb, 1);

      sb.append(CELL_WALL_CHAR);
      for (int i = 0; i < colCount; i++) {
        sb.append(centerString(widthMap.get(i) + 4, tableToRender.headers.get(i))).append(CELL_WALL_CHAR);
      }
      newLine(sb, 1);
    }
    createBorder(sb, colCount, widthMap);

    for (List<Comparable<? extends Object>> row : tableToRender.rows) {
      newLine(sb, 1);
      sb.append(CELL_WALL_CHAR);
      for (int i = 0; i < row.size(); i++) {
        sb.append(centerString(widthMap.get(i) + 4, row.get(i).toString())).append(CELL_WALL_CHAR);
      }
    }

    newLine(sb, 1);
    createBorder(sb, colCount, widthMap);
    newLine(sb, 1);

    return sb.toString();
  }

  private void newLine(StringBuilder sb, int count) {
    for (int i = 0; i < count; i++) {
      sb.append('\n');
    }
  }

  private String centerString(int width, String s) {
    s = ensureEvenLength(s);
    return String.format(Locale.ROOT, "%-" + width + "s", String.format(Locale.ROOT, "%" + (s.length() + (width - s.length()) / 2) + "s", s));
  }

  private String ensureEvenLength(String s) {
    if (s.length() % 2 != 0) {
      s = s + " ";
    }
    return s;
  }

  private void createBorder(StringBuilder sb, int headerCount, Map<Integer, Integer> widthMap) {
    for (int i = 0; i < headerCount; i++) {
      if (i == 0) {
        sb.append(CELL_CORNER_CHAR);
      }

      for (int j = 0; j < widthMap.get(i) + CELL_PAD_SIZE * 2; j++) {
        sb.append(CELL_DASH_CHAR);
      }
      sb.append(CELL_CORNER_CHAR);
    }
  }

  private Map<Integer, Integer> getWidthMap() {
    Map<Integer, Integer> map = new HashMap<>();
    String cellValue = null;
    String headerValue = null;

    // set max's to header sizes for each col
    for (int i = 0; i < tableToRender.headers.size(); i++) {
      headerValue = ensureEvenLength(tableToRender.headers.get(i));
      map.put(i, headerValue.length());
    }
    // if there are any cell values longer than the header length set max to longest
    // cell value length
    for (List<Comparable<? extends Object>> row : tableToRender.rows) {
      for (int i = 0; i < row.size(); i++) {
        Object o = row.get(i);
        if (o == null) {
          o = "NULL";
          row.set(i, (String) o);
        }
        cellValue = ensureEvenLength(o.toString());
        if (map.get(i) == null || cellValue.length() > map.get(i)) {
          map.put(i, cellValue.length());
        }
      }
    }
    return map;
  }

}
