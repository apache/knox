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
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/**
 * A custom @JsonDeserializer in order to be able to deserialize a previously
 * serialized {@link KnoxShellTable}. It is required because
 * <ul>
 * <li>Jackson is not capable of constructing instance of `java.lang.Comparable`
 * (which we have in the table cells)</li>
 * <li><code>callHistory</code> deserialization requires special handling
 * </ul>
 *
 */
@SuppressWarnings("serial")
public class KnoxShellTableRowDeserializer extends StdDeserializer<KnoxShellTable> {

  private final KnoxShellTable table;

  KnoxShellTableRowDeserializer(KnoxShellTable table) {
    this(KnoxShellTable.class, table);
  }

  protected KnoxShellTableRowDeserializer(Class<?> vc, KnoxShellTable table) {
    super(vc);
    this.table = table;
  }

  @Override
  public KnoxShellTable deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {
    final TreeNode jsonContent = parser.readValueAsTree();
    if (jsonContent.get("callHistoryList") != null) {
      return parseJsonWithCallHistory(jsonContent);
    } else {
      return parseJsonWithData(jsonContent);
    }
  }

  private KnoxShellTable parseJsonWithCallHistory(TreeNode jsonContent) throws IOException {
    final List<KnoxShellTableCall> calls = parseCallHistoryListJSONNode(jsonContent.get("callHistoryList"));
    long tempId = KnoxShellTable.getUniqueTableId();
    KnoxShellTableCallHistory.getInstance().saveCalls(tempId, calls);
    final KnoxShellTable tableFromJson = KnoxShellTableCallHistory.getInstance().replay(tempId, calls.size());
    table.rows = tableFromJson.rows;
    KnoxShellTableCallHistory.getInstance().removeCallsById(tempId);
    KnoxShellTableCallHistory.getInstance().removeCallsById(tableFromJson.id);
    return table;
  }

  private List<KnoxShellTableCall> parseCallHistoryListJSONNode(TreeNode callHistoryNode) throws IOException {
    final List<KnoxShellTableCall> callHistoryList = new LinkedList<>();
    TreeNode callNode;
    Map<Object, Class<?>> params;
    String invokerClass, method;
    Boolean builderMethod;
    for (int i = 0; i < callHistoryNode.size(); i++) {
      callNode = callHistoryNode.get(i);
      invokerClass = trimJSONQuotes(callNode.get("invokerClass").toString());
      method = trimJSONQuotes(callNode.get("method").toString());
      builderMethod = Boolean.valueOf(trimJSONQuotes(callNode.get("builderMethod").toString()));
      params = fetchParameterMap(callNode.get("params"));
      callHistoryList.add(new KnoxShellTableCall(invokerClass, method, builderMethod, params));
    }
    return callHistoryList;
  }

  private Map<Object, Class<?>> fetchParameterMap(TreeNode paramsNode) throws IOException {
    try {
      final Map<Object, Class<?>> parameterMap = new HashMap<>();
      final Iterator<String> paramsFieldNamesIterator = paramsNode.fieldNames();
      String parameterValueAsString;
      Class<?> parameterType;
      while (paramsFieldNamesIterator.hasNext()) {
        parameterValueAsString = trimJSONQuotes(paramsFieldNamesIterator.next());
        parameterType = Class.forName(trimJSONQuotes(paramsNode.get(parameterValueAsString).toString()));
        parameterMap.put(cast(parameterValueAsString, parameterType), parameterType);
      }
      return parameterMap;
    } catch (Exception e) {
      throw new IOException("Error while fetching parameters " + paramsNode, e);
    }
  }

  // This may be done in a different way or using a library; I did not find any (I
  // did not do a deep search though)
  private Object cast(String valueAsString, Class<?> type) throws ParseException {
    if (String.class == type) {
      return valueAsString;
    } else if (Byte.class == type) {
      return Byte.valueOf(valueAsString);
    } else if (Short.class == type) {
      return Short.valueOf(valueAsString);
    } else if (Integer.class == type) {
      return Integer.valueOf(valueAsString);
    } else if (Long.class == type) {
      return Long.valueOf(valueAsString);
    } else if (Float.class == type) {
      return Float.valueOf(valueAsString);
    } else if (Double.class == type) {
      return Double.valueOf(valueAsString);
    } else if (Boolean.class == type) {
      return Boolean.valueOf(valueAsString);
    } else if (Date.class == type) {
      return KnoxShellTableJSONSerializer.JSON_DATE_FORMAT.get().parse(valueAsString);
    }

    return type.cast(valueAsString); // may throw ClassCastException
  }

  private KnoxShellTable parseJsonWithData(final TreeNode jsonContent) {
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
   * When serializing an object as JSON all elements within the table receive a
   * surrounding quote pair (e.g. the cell contains myValue -> the JSON serialized
   * string will be "myValue")
   */
  private String trimJSONQuotes(String toBeTrimmed) {
    return toBeTrimmed.replaceAll("\"", "");
  }

}
