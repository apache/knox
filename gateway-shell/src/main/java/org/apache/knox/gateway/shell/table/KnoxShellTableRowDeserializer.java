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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * A custom @JsonDeserializer in order to be able to deserialize a previously
 * serialzed @KnoxShellTable. It is requiored because Jackson is not capable of
 * constructing instance of `java.lang.Comparable` (which we have int he table
 * cells)
 *
 */
@SuppressWarnings("serial")
public class KnoxShellTableRowDeserializer extends StdDeserializer<KnoxShellTable> {

  KnoxShellTableRowDeserializer() {
    this(KnoxShellTable.class);
  }

  protected KnoxShellTableRowDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public KnoxShellTable deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
    final TreeNode jsonContent = parser.readValueAsTree();
    final KnoxShellTable table = new KnoxShellTable();
    if (jsonContent.get("title").size() != 0) {
      table.title(trimJSONQuotes(jsonContent.get("title").toString()));
    }

    final TreeNode headers = jsonContent.get("headers");
    for (int i = 0; i < headers.size(); i++) {
      table.header(trimJSONQuotes(headers.get(i).toString()));
    }
    final TreeNode rows = jsonContent.get("rows");
    for (int i = 0; i < rows.size(); i++) {
      TreeNode row = rows.get(i);
      table.row();
      for (int j = 0; j < row.size(); j++) {
        table.value(trimJSONQuotes(row.get(j).toString()));
      }
    }
    return table;
  }

  /*
   * When serializing an object as JSON all elements within the table receive a surrounding quote pair (e.g. the cell contains myValue -> the JSON serialized string will be "myValue")
   */
  private String trimJSONQuotes(String toBeTrimmed) {
    return toBeTrimmed.replaceAll("\"", "");
  }

}
