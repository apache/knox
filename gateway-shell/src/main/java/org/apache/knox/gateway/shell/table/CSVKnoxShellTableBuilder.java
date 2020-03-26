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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class CSVKnoxShellTableBuilder extends KnoxShellTableBuilder {

  private boolean withHeaders;

  CSVKnoxShellTableBuilder(KnoxShellTable table) {
    super(table);
  }

  public CSVKnoxShellTableBuilder withHeaders() {
    withHeaders = true;
    return this;
  }

  public KnoxShellTable url(String url) throws IOException {
    URL urlToCsv = new URL(url);
    URLConnection connection = urlToCsv.openConnection();
    try (Reader urlConnectionStreamReader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        BufferedReader csvReader = new BufferedReader(urlConnectionStreamReader);) {
      buildTableFromCSVReader(csvReader);
    }
    return this.table;
  }

  public KnoxShellTable string(String csvString) throws IOException {
    try (InputStream is = new ByteArrayInputStream(csvString.getBytes(StandardCharsets.UTF_8));
        Reader stringStreamReader = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader csvReader = new BufferedReader(stringStreamReader);) {
      buildTableFromCSVReader(csvReader);
    }
    return this.table;
  }

  private void buildTableFromCSVReader(BufferedReader csvReader) throws IOException {
    int rowIndex = 0;
    if (title != null) {
      this.table.title(title);
    }
    String row = null;
    while ((row = csvReader.readLine()) != null) {
      boolean addingHeaders = (withHeaders && rowIndex == 0);
      if (!addingHeaders) {
        this.table.row();
      }
      // handle comma's within quoted string values for single col
      String[] data = row.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);

      for (String value : data) {
        if (addingHeaders) {
          this.table.header(value);
        } else {
          this.table.value(value);
        }
      }
      rowIndex++;
    }
  }

}
