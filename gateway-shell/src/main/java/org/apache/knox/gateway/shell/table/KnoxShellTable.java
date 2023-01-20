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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SortOrder;
import com.fasterxml.jackson.annotation.JsonFilter;

import org.apache.commons.math3.stat.StatUtils;

/**
 * Simple table representation and text based rendering of a table via
 * toString(). Headers are optional but when used must have the same count as
 * columns within the rows.
 */
@JsonFilter("knoxShellTableFilter")
public class KnoxShellTable {

    private enum Conversions {
        DOUBLE,
        INTEGER,
        FLOAT,
        BYTE,
        SHORT,
        LONG,
        STRING
    }

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  List<String> headers = new ArrayList<>();
  List<List<Comparable<? extends Object>>> rows = new ArrayList<>();
  String title;
  long id;

  public KnoxShellTable() {
    this.id = getUniqueTableId();
  }

  public KnoxShellTable title(String title) {
    this.title = title;
    return this;
  }

  public KnoxShellTable header(String header) {
    headers.add(header.trim());
    return this;
  }

  public KnoxShellTable row() {
    rows.add(new ArrayList<>());
    return this;
  }

  public KnoxShellTable value(Comparable<? extends Object> value) {
    final int index = rows.isEmpty() ? 0 : rows.size() - 1;
    final List<Comparable<? extends Object>> row = rows.get(index);
    row.add(value);
    return this;
  }

  public KnoxShellTableCell<? extends Comparable<? extends Object>> cell(int colIndex, int rowIndex) {
    return new KnoxShellTableCell(headers, rows, colIndex, rowIndex);
  }

  public List<Comparable<? extends Object>> values(int colIndex) {
    List<Comparable<? extends Object>> col = new ArrayList<>();
    rows.forEach(row -> col.add(row.get(colIndex)));
    return col;
  }

  public List<Comparable<? extends Object>> values(String colName) {
    int colIndex = headers.indexOf(colName);
    List<Comparable<? extends Object>> col = new ArrayList<>();
    rows.forEach(row -> col.add(row.get(colIndex)));
    return col;
  }

  private Conversions getConversion(Comparable<? extends Object> colIndex) {
    Conversions type = null;
    if (colIndex instanceof Double) {
      type = Conversions.DOUBLE;
    }
    else if (colIndex instanceof Integer) {
      type = Conversions.INTEGER;
    }
    else if (colIndex instanceof Float) {
      type = Conversions.FLOAT;
    }
    else if (colIndex instanceof Byte) {
      type = Conversions.BYTE;
    }
    else if (colIndex instanceof Short) {
      type = Conversions.SHORT;
    }
    else if (colIndex instanceof Long) {
      type = Conversions.LONG;
    }
    else if (colIndex instanceof String) {
      if (((String) colIndex).matches("-?\\d+(\\.\\d+)?")) {
          type = Conversions.STRING;
      }
      else {
        throw new IllegalArgumentException("String contains non-numeric characters");
      }
    }
    else {
        throw new IllegalArgumentException("Unsupported data type");
    }
    return type;
  }

  private double[] toDoubleArray(String colName) throws IllegalArgumentException {
    List<Comparable<? extends Object>> col = values(colName);
    double[] colArray = new double[col.size()];
    Conversions conversionMethod = null;
    for (int i = 0; i < col.size(); i++) {
      Object v = col.get(i);
      if (v instanceof String && ((String) v).trim().isEmpty()) {
        col.set(i, "0");
      }
      if (i == 0) {
        conversionMethod = getConversion(col.get(i));
      }
      switch (conversionMethod) {
        case DOUBLE:
          colArray[i] = (Double) col.get(i);
          break;
        case INTEGER:
          colArray[i] = (Integer) col.get(i);
          break;
        case FLOAT:
          colArray[i] = (Float) col.get(i);
          break;
        case BYTE:
          colArray[i] = (Byte) col.get(i);
          break;
        case SHORT:
          colArray[i] = (Short) col.get(i);
          break;
        case LONG:
          colArray[i] = (double) (Long) col.get(i);
          break;
        case STRING:
          colArray[i] = Double.parseDouble((String) col.get(i));
          break;
      }
    }
    return colArray;
  }

  /**
   * Calculates the mean of specified column
   * @param colName the column for which the mean will be calculated
   * @return mean
   */
  public double mean(String colName) {
    return StatUtils.mean(toDoubleArray(colName));
  }

  /**
   * Calculates the mean of specified column
   * @param colIndex the column for which the mean will be calculated
   * @return mean
   */
  public double mean(int colIndex) {
    return mean(headers.get(colIndex));
  }

  /**
   * Calculates the median of specified column
   * @param colName the column for which the median will be calculated
   * @return median
   */
  public double median(String colName) {
    return StatUtils.percentile(toDoubleArray(colName), 50);
  }

  /**
   * Calculates the median of specified column
   * @param colIndex the column for which the median will be calculated
   * @return median
   */
  public double median(int colIndex) {
    return median(headers.get(colIndex));
  }

  /**
   * Calculates the mode of specified column
   * @param colName the column for which the mode will be calculated
   * @return mode
   */
  public double mode(String colName) {
    return StatUtils.mode(toDoubleArray(colName))[0];
  }

  /**
   * Calculates the mode of specified column
   * @param colIndex the column for which the mode will be calculated
   * @return mode
   */
  public double mode(int colIndex) {
    return mode(headers.get(colIndex));
  }

  /**
   * Calculates the sum of specified column
   * @param colName the column for which the sum will be calculated
   * @return sum
   */
  public double sum(String colName) {
    return StatUtils.sum(toDoubleArray(colName));
  }

  /**
   * Calculates the sum of specified column
   * @param colIndex the column for which the sum will be calculated
   * @return sum
   */
  public double sum(int colIndex) {
    return sum(headers.get(colIndex));
  }

  /**
   * Calculates the max of specified column
   * @param colName the column for which the max will be calculated
   * @return max
   */
  public double max(String colName) {
    return StatUtils.max(toDoubleArray(colName));
  }

  /**
   * Calculates the max of specified column
   * @param colIndex the column for which the max will be calculated
   * @return max
   */
  public double max(int colIndex) {
    return max(headers.get(colIndex));
  }

  /**
   * Calculates the min of specified column
   * @param colName the column for which the min will be calculated
   * @return min
   */
  public double min(String colName) {
    return StatUtils.min(toDoubleArray(colName));
  }

  /**
   * Calculates the min of specified column
   * @param colIndex the column for which the min will be calculated
   * @return min
   */
  public double min(int colIndex) {
    return min(headers.get(colIndex));
  }

  public KnoxShellTable apply(KnoxShellTableCell<? extends Comparable<? extends Object>> cell) {
    if (!headers.isEmpty()) {
      headers.set(cell.colIndex, cell.header);
    }
    if (!rows.isEmpty()) {
      rows.get(cell.rowIndex).set(cell.colIndex, cell.value);
    }
    return this;
  }

  /**
   * Trims the String value of whitespace for each of the values in a column
   * given the column name.
   * @param colIndex
   * @return table
   */
  public KnoxShellTable trim(String colName) {
    int colIndex = headers.indexOf(colName);
    return trim(colIndex);
  }

  /**
   * Trims the String value of whitespace for each of the values in a column
   * given the column index.
   * @param colIndex
   * @return table
   */
  public KnoxShellTable trim(int colIndex) {
    List<Comparable<? extends Object>> col = values(colIndex);
    for (int i = 0; i < col.size(); i++) {
      String v = (String) col.get(i);
      rows.get(i).set(colIndex, v.trim());
    }
    return this;
  }

  public List<String> getHeaders() {
    return headers == null || headers.isEmpty() ? null : headers;
  }

  public List<List<Comparable<? extends Object>>> getRows() {
    return rows;
  }

  public String getTitle() {
    return title;
  }

  public long getId() {
    return id;
  }

  public static KnoxShellTableBuilder builder() {
    return new KnoxShellTableBuilder();
  }

  static long getUniqueTableId() {
    return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000);
  }

  public List<KnoxShellTableCall> getCallHistoryList() {
    final List<KnoxShellTableCall> sanitizedCallHistoryList = new LinkedList<>();
    KnoxShellTableCallHistory.getInstance().getCallHistory(id).forEach(call -> {
      final Map<Object, Class<?>> params = call.hasSensitiveData() ? Collections.singletonMap("***", String.class) : call.getParams();
      sanitizedCallHistoryList.add(new KnoxShellTableCall(call.getInvokerClass(), call.getMethod(), call.isBuilderMethod(), params));
    });
    return sanitizedCallHistoryList;
  }

  public String getCallHistory() {
    final StringBuilder callHistoryStringBuilder = new StringBuilder("Call history (id=")
        .append(id).append(')').append(LINE_SEPARATOR).append(LINE_SEPARATOR);
    final AtomicInteger index = new AtomicInteger(1);
    getCallHistoryList().forEach(callHistory -> callHistoryStringBuilder.append("Step ")
        .append(index.getAndIncrement()).append(':').append(LINE_SEPARATOR).append(callHistory).append(LINE_SEPARATOR));
    return callHistoryStringBuilder.toString();
  }

  public String rollback() {
    final KnoxShellTable rolledBack = KnoxShellTableCallHistory.getInstance().rollback(id);
    this.id = rolledBack.id;
    this.title = rolledBack.title;
    this.headers = rolledBack.headers;
    this.rows = rolledBack.rows;
    return "Successfully rolled back";
  }

  public KnoxShellTable replayAll() {
    final int step = KnoxShellTableCallHistory.getInstance().getCallHistory(id).size();
    return replay(step);
  }

  public KnoxShellTable replay(int step) {
    return replay(id, step);
  }

  public static KnoxShellTable replay(long id, int step) {
    return KnoxShellTableCallHistory.getInstance().replay(id, step);
  }

  public KnoxShellTableFilter filter() {
    return new KnoxShellTableFilter(this);
  }

  public KnoxShellTableAggregator aggregate() {
    return new KnoxShellTableAggregator(this);
  }

  public KnoxShellTable select(String cols) {
    KnoxShellTable table = new KnoxShellTable();
    List<List<Comparable<? extends Object>>> columns = new ArrayList<>();
    cols = cols.trim();
    String[] colnames = cols.split("\\s*,\\s*");
    for (String colName : colnames) {
      table.header(colName);
      columns.add(values(headers.indexOf(colName)));
    }
    for (int i = 0; i < rows.size(); i++) {
      table.row();
      for (List<Comparable<? extends Object>> col : columns) {
        table.value(col.get(i));
      }
    }
    return table;
  }

  public KnoxShellTable sort(String colName) {
    return sort(colName, SortOrder.ASCENDING);
  }

  public KnoxShellTable sortNumeric(String colName) {
    double[] col = toDoubleArray(colName);
    ArrayList<Comparable<? extends Object>> column = new ArrayList<>();
    for(double v : col) {
      column.add(v);
   }
    return sort(column, SortOrder.ASCENDING);
  }

  public KnoxShellTable sort(String colName, SortOrder order) {
    List<Comparable<? extends Object>> col = values(colName);
    return sort(col, order);
  }

  public KnoxShellTable sort(List<Comparable<? extends Object>> col,
      SortOrder order) {
    KnoxShellTable table = new KnoxShellTable();

    Comparable<? extends Object> value;
    List<RowIndex> index = new ArrayList<>();
    for (int i = 0; i < col.size(); i++) {
      value = col.get(i);
      index.add(new RowIndex(value, i));
    }
    if (SortOrder.ASCENDING.equals(order)) {
      Collections.sort(index);
    }
    else {
      index.sort(Collections.reverseOrder());
    }
    table.headers = new ArrayList<>(headers);
    for (RowIndex i : index) {
      table.rows.add(new ArrayList<>(this.rows.get(i.index)));
    }
    return table;
  }

  private static class RowIndex implements Comparable<RowIndex> {
    Comparable value;
    int index;

    RowIndex(Comparable<? extends Object> value, int index) {
      this.value = value;
      this.index = index;
    }

    @Override
    public int compareTo(RowIndex other) {
      return this.value.compareTo(other.value);
    }
  }

  @Override
  public String toString() {
    return new KnoxShellTableRenderer(this).toString();
  }

  public String toJSON() {
    return toJSON((String) null);
  }

  public String toJSON(boolean data) {
    return toJSON(data, null);
  }

  public String toJSON(String path) {
    return toJSON(true, path);
  }

  public String toJSON(boolean data, String path) {
    return KnoxShellTableJSONSerializer.serializeKnoxShellTable(this, data, path);
  }

  public String toCSV() {
    return toCSV(null);
  }

  public String toCSV(String filePath) {
    return new KnoxShellTableRenderer(this).toCSV(filePath);
  }
}
