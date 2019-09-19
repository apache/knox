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
package org.apache.knox.gateway.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.util.JsonUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
* Simple table representation and text based rendering of a table via toString().
* Headers are optional but when used must have the same count as columns
* within the rows.
*/
public class KnoxShellTable {
  private static int CELL_PAD_SIZE = 2;
  private static char CELL_CORNER_CHAR = '+';
  private static char CELL_WALL_CHAR = '|';
  private static char CELL_DASH_CHAR = '-';

  private List<String> headers = new ArrayList<String>();
  private List<List<String>> rows = new ArrayList<List<String>>();
  private String title;

  public KnoxShellTable title(String title) {
    this.title = title;
    return this;
  }

  public KnoxShellTable header(String header) {
    headers.add(header);
    return this;
  }

  public KnoxShellTable row() {
    List<String> row = new ArrayList<String>();
    rows.add(row);
    return this;
  }

  public KnoxShellTable value(String value) {
    int index = rows.size() - 1;
    if (index == -1) {
      index = 0;
    }
    List<String> row = rows.get(index);
    row.add(value);
    return this;
  }

  public KnoxShellTableCell cell(int colIndex, int rowIndex) {
    return new KnoxShellTableCell(colIndex, rowIndex);
  }

  public List<String> values(int colIndex) {
    ArrayList<String> col = new ArrayList<String>();
    rows.forEach(row -> col.add(row.get(colIndex)));
    return col;
  }

  public List<String> values(String colName) {
    int colIndex = headers.indexOf(colName);
    ArrayList<String> col = new ArrayList<String>();
    rows.forEach(row -> col.add(row.get(colIndex)));
    return col;
  }

  public KnoxShellTable apply(KnoxShellTableCell cell) {
    if (!headers.isEmpty()) {
      headers.set(cell.colIndex, cell.header);
    }
    if (!rows.isEmpty()) {
      rows.get(cell.rowIndex).set(cell.colIndex, cell.value);
    }
    return this;
  }

  public class KnoxShellTableCell {
    private int colIndex;
    private int rowIndex;
    private String header;
    private String value;

    KnoxShellTableCell(int colIndex, int rowIndex) {
      this.colIndex = colIndex;
      this.rowIndex = rowIndex;
      if (!headers.isEmpty()) {
        this.header = headers.get(colIndex);
      }
      if (!rows.isEmpty()) {
        this.value = rows.get(rowIndex).get(colIndex);
      }
    }

    KnoxShellTableCell(String name, int rowIndex) {
      this.rowIndex = rowIndex;
      if (!headers.isEmpty()) {
        this.header = name;
        this.colIndex = headers.indexOf(name);
      }
      if (!rows.isEmpty()) {
        this.value = rows.get(rowIndex).get(colIndex);
      }
    }

    public KnoxShellTableCell value(String value) {
      this.value = value;
      return this;
    }

    public KnoxShellTableCell header(String name) {
      this.header = name;
      return this;
    }

    public String value() {
      return this.value;
    }

    public String header() {
      return this.header;
    }
  }

  public List<String> getHeaders() {
    if (headers.isEmpty()) {
      return null;
    }
    return headers;
  }

  public List<List<String>> getRows() {
    return rows;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public String toString() {
    if (!headers.isEmpty() && !rows.isEmpty() && headers.size() != rows.get(0).size()) {
      throw new IllegalStateException("Number of columns and headers must be the same.");
    }
    StringBuilder sb = new StringBuilder();
    Map<Integer, Integer> widthMap = getWidthMap();

    if (title != null && !title.isEmpty()) {
      sb.append(this.title);
      newLine(sb, 1);
    }
    int colCount = 0;
    if (!rows.isEmpty()) {
      colCount = rows.get(0).size();
    }
    if (!headers.isEmpty()) {
      colCount = headers.size();
      createBorder(sb, colCount, widthMap);
      newLine(sb, 1);

      sb.append(CELL_WALL_CHAR);
      for (int i = 0; i < colCount; i++) {
        sb.append(centerString(widthMap.get(i) + 4, headers.get(i))).append(CELL_WALL_CHAR);
      }
      newLine(sb, 1);
    }
    createBorder(sb, colCount, widthMap);

    for (List<String> row : rows) {
      newLine(sb, 1);
      sb.append(CELL_WALL_CHAR);
      for (int i = 0; i < row.size(); i++) {
        sb.append(centerString(widthMap.get(i) + 4, row.get(i))).append(CELL_WALL_CHAR);
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
    for (int i = 0; i < headers.size(); i++) {
      headerValue = ensureEvenLength(headers.get(i));
      map.put(i, headerValue.length());
    }
    // if there are any cell values longer than the header length set max to longest
    // cell value length
    for (List<String> row : rows) {
      for (int i = 0; i < row.size(); i++) {
        cellValue = ensureEvenLength(row.get(i));
        if (map.get(i) == null || cellValue.length() > map.get(i)) {
          map.put(i, cellValue.length());
        }
      }
    }
    return map;
  }

  public static KnoxShellTableBuilder builder() {
    return new KnoxShellTableBuilder();
  }

  public static class KnoxShellTableBuilder {
    protected String title;

    public KnoxShellTableBuilder title(String title) {
      this.title = title;
      return this;
    }

    public CSVKnoxShellTableBuilder csv() {
      return new CSVKnoxShellTableBuilder();
    }

    public JSONKnoxShellTableBuilder json() {
      return new JSONKnoxShellTableBuilder();
    }

    public JoinKnoxShellTableBuilder join() {
      return new JoinKnoxShellTableBuilder();
    }

    public JDBCKnoxShellTableBuilder jdbc() {
      return new JDBCKnoxShellTableBuilder();
    }
  }

  public static class JoinKnoxShellTableBuilder extends KnoxShellTableBuilder {
    private KnoxShellTable left;
    private KnoxShellTable right;
    private int leftIndex = -1;
    private int rightIndex = -1;

    public JoinKnoxShellTableBuilder() {
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

    public KnoxShellTable on(int leftIndex, int rightIndex) {
      KnoxShellTable joined = new KnoxShellTable();
      if (title != null) {
        joined.title(title);
      }

      this.leftIndex = leftIndex;
      this.rightIndex = rightIndex;

      joined.headers.addAll(new ArrayList<String>(left.headers));
      for (List<String> row : left.rows) {
        joined.rows.add(new ArrayList<String>(row));
      }
      List<String> col = right.values(rightIndex);
      ArrayList<String> row;
      String leftKey;
      int matchedIndex;

      joined.headers.addAll(new ArrayList<String>(right.headers));
      for (Iterator<List<String>> it = joined.rows.iterator(); it.hasNext();) {
        row = (ArrayList<String>) it.next();
        leftKey = row.get(leftIndex);
        if (leftKey != null) {
          matchedIndex = col.indexOf(leftKey);
          if (matchedIndex > -1) {
            row.addAll(right.rows.get(matchedIndex));
          }
          else {
            it.remove();
          }
        }
      }
      return joined;
    }
  }

  public static class JSONKnoxShellTableBuilder extends KnoxShellTableBuilder {
    boolean withHeaders;

    @Override
    public JSONKnoxShellTableBuilder title(String title) {
      this.title = title;
      return this;
    }

    public JSONKnoxShellTableBuilder withHeaders() {
      withHeaders = true;
      return this;
    }

    public KnoxShellTable string(String json) throws IOException {
      KnoxShellTable table = getKnoxShellTableFromJsonString(json);
      return table;
    }

    public KnoxShellTable path(String path) throws IOException {
      String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
      KnoxShellTable table = getKnoxShellTableFromJsonString(json);
      return table;
    }

    public KnoxShellTable getKnoxShellTableFromJsonString(String json) throws IOException {
      KnoxShellTable table = null;
      JsonFactory factory = new JsonFactory();
      ObjectMapper mapper = new ObjectMapper(factory);
      TypeReference<KnoxShellTable> typeRef
            = new TypeReference<KnoxShellTable>() {};
      table = mapper.readValue(json, typeRef);
      if (title != null) {
        table.title(title);
      }
      return table;
    }
  }

  public static class CSVKnoxShellTableBuilder extends KnoxShellTableBuilder {
    boolean withHeaders;

    @Override
    public CSVKnoxShellTableBuilder title(String title) {
      this.title = title;
      return this;
    }

    public CSVKnoxShellTableBuilder withHeaders() {
      withHeaders = true;
      return this;
    }

    public KnoxShellTable url(String url) throws IOException {
      int rowIndex = 0;
      URLConnection connection;
      BufferedReader csvReader = null;
      KnoxShellTable table = null;
      try {
        URL urlToCsv = new URL(url);
        connection = urlToCsv.openConnection();
        csvReader = new BufferedReader(new InputStreamReader(
            connection.getInputStream(), StandardCharsets.UTF_8));
        table = new KnoxShellTable();
        if (title != null) {
          table.title(title);
        }
        String row = null;
        while ((row = csvReader.readLine()) != null) {
            boolean addingHeaders = (withHeaders && rowIndex == 0);
            if (!addingHeaders) {
              table.row();
            }
            String[] data = row.split(",");

            for (String value : data) {
              if (addingHeaders) {
                table.header(value);
              }
              else {
                table.value(value);
              }
            }
            rowIndex++;
        }
      }
      finally {
        csvReader.close();
      }
      return table;
    }
  }

  public static class JDBCKnoxShellTableBuilder extends KnoxShellTableBuilder {
    private String connect;
    private String username;
    private String pwd;
    private String driver;
    private Connection conn;
    private boolean tableManagedConnection = true;

    @Override
    public JDBCKnoxShellTableBuilder title(String title) {
      this.title = title;
      return this;
    }

    public JDBCKnoxShellTableBuilder connect(String connect) {
      this.connect = connect;
      return this;
    }

    public JDBCKnoxShellTableBuilder username(String username) {
      this.username = username;
      return this;
    }

    public JDBCKnoxShellTableBuilder pwd(String pwd) {
      this.pwd = pwd;
      return this;
    }

    public JDBCKnoxShellTableBuilder driver(String driver) {
      this.driver = driver;
      return this;
    }

    public JDBCKnoxShellTableBuilder connection(Connection connection) {
      this.conn = connection;
      this.tableManagedConnection = false;
      return this;
    }

    public KnoxShellTable sql(String sql) throws IOException, SQLException {
      KnoxShellTable table = null;
      Statement statement = null;
      ResultSet result = null;
      if (conn == null) {
        conn = DriverManager.getConnection(connect);
      }
      try {
        if (conn != null) {
          statement = conn.createStatement();
  //table.builder().jdbc().connect("jdbc:derby:codejava/webdb1").username("lmccay").password("xxxx").sql("SELECT * FROM book");
          result = statement.executeQuery(sql);
          table = new KnoxShellTable();
          ResultSetMetaData metadata = result.getMetaData();
          table.title(metadata.getTableName(1));
          int colcount = metadata.getColumnCount();
          for(int i = 1; i < colcount + 1; i++) {
            table.header(metadata.getColumnName(i));
          }
          while (result.next()) {
            table.row();
            for(int i = 1; i < colcount + 1; i++) {
              table.value(result.getString(metadata.getColumnName(i)));
            }
          }
        }
      }
      finally {
        result.close();
        if (conn != null && tableManagedConnection) {
          conn.close();
        }
        if (statement != null) {
          statement.close();
        }
        if (result != null && !result.isClosed()) {
          result.close();
        }
      }
      return table;
    }
  }

  public String toJSON() {
    return JsonUtils.renderAsJsonString(this);
  }

  public String toCSV() {
    StringBuilder csv = new StringBuilder();
    String header;
    for(int i = 0; i < headers.size(); i++) {
      header = headers.get(i);
      csv.append(header);
      if (i < headers.size() - 1) {
        csv.append(',');
      }
      else {
        csv.append('\n');
      }
    }
    for(List<String> row : rows) {
      for(int ii = 0; ii < row.size(); ii++) {
        csv.append(row.get(ii));
        if (ii < row.size() - 1) {
          csv.append(',');
        }
        else {
          csv.append('\n');
        }
      }
    }

    return csv.toString();
  }

  public KnoxShellTable select(String cols) {
    KnoxShellTable table = new KnoxShellTable();
    List<ArrayList<String>> columns = new ArrayList<ArrayList<String>>();
    String[] colnames = cols.split(",");
    for (String colName : colnames) {
      table.header(colName);
      columns.add((ArrayList<String>) values(headers.indexOf(colName)));
    }
    for (int i = 0; i < rows.size(); i ++) {
      table.row();
      for (List<String> col : columns) {
        table.value(col.get(i));
      }
    }
    return table;
  }

  public KnoxShellTable sort(String colName) {
    KnoxShellTable table = new KnoxShellTable();

    String value;
    List<String> col = values(colName);
    List<RowIndex> index = new ArrayList<RowIndex>();
    for (int i = 0; i < col.size(); i++) {
      value = col.get(i);
      index.add(new RowIndex(value, i));
    }
    Collections.sort(index);
    table.headers = new ArrayList<String>(headers);
    for (RowIndex i : index) {
      table.rows.add(new ArrayList<String>(this.rows.get(i.index)));
    }
    return table;
  }

  public static class RowIndex implements Comparable<RowIndex> {
    String value;
    int index;

    public RowIndex(String value, int index) {
      this.value = value;
      this.index = index;
    }

    @Override
    public int compareTo(RowIndex other) {
      return (this.value.compareTo(other.value));
    }
  }

  public KnoxShellTableFilter filter() {
    return new KnoxShellTableFilter();
  }

  public class KnoxShellTableFilter {
    String name;
    int index;

    public KnoxShellTableFilter name(String name) {
      this.name = name;
      index = headers.indexOf(name);
      return this;
    }

    public KnoxShellTableFilter index(int index) {
      this.index = index;
      return this;
    }

    public KnoxShellTable regex(String regex) {
      KnoxShellTable table = new KnoxShellTable();
      table.headers.addAll(headers);
      for (List<String> row : rows) {
        if (Pattern.matches(regex, row.get(index))) {
          table.row();
          row.forEach(value -> {
            table.value(value);
          });
        }
      }
      return table;
    }
  }
}
