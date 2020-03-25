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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.shell.KnoxShellException;
import org.apache.knox.gateway.util.JsonUtils;

import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

/**
 * Utility class that helps serializing a {@link KnoxShellTable} in JSON format. The
 * reasons this class exists are:
 * <ol>
 * <li>to define the @DateFormat we use when serializing/deserializing a @Date
 * Object</li>
 * <li>conditionally exclude certain fields from the serialized JSON
 * representation</li>
 * </ol>
 */
class KnoxShellTableJSONSerializer {

  // SimpleDateFormat is not thread safe must use as a ThreadLocal
  static final ThreadLocal<DateFormat> JSON_DATE_FORMAT = ThreadLocal.withInitial(() ->
       new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()));

  /**
   * Serializes the given {@link KnoxShellTable}
   *
   * @param table
   *          the table to be serialized
   * @param data
   *          if this is <code>true</code> the underlying JSON serializer will
   *          output the table's content; otherwise the table's
   *          <code>callHistory</code> will be serilized
   * @param filePath
   *          if set, the JSON result will be written into the given file
   *          (creating if not exists; overwritten if exists)
   * @return the serialized table in JSON format
   */
  static String serializeKnoxShellTable(KnoxShellTable table, boolean data, String filePath) {
    if (StringUtils.isNotBlank(filePath)) {
      return saveTableInFile(table, data, filePath);
    } else {
      final SimpleFilterProvider filterProvider = new SimpleFilterProvider();
      if (data) {
        filterProvider.addFilter("knoxShellTableFilter", SimpleBeanPropertyFilter.filterOutAllExcept("headers", "rows", "title", "id"));
      } else {
        filterProvider.addFilter("knoxShellTableFilter", SimpleBeanPropertyFilter.filterOutAllExcept("callHistoryList"));
      }
      return JsonUtils.renderAsJsonString(table, filterProvider, JSON_DATE_FORMAT.get());
    }
  }

  private static String saveTableInFile(KnoxShellTable table, boolean data, String filePath) {
    try {
      final String jsonResult;
      if (data) {
        final SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter("knoxShellTableFilter", SimpleBeanPropertyFilter.filterOutAllExcept("headers", "rows", "title", "id"));
        jsonResult = JsonUtils.renderAsJsonString(table, filterProvider, JSON_DATE_FORMAT.get());
      } else {
        jsonResult = JsonUtils.renderAsJsonString(KnoxShellTableCallHistory.getInstance().getCallHistory(table.id), null, JSON_DATE_FORMAT.get());
      }
      KnoxShellTableFileUtils.persistToFile(filePath, jsonResult);
      return "Successfully saved into " + filePath;
    } catch (IOException e) {
      throw new KnoxShellException("Error while saving KnoxShellTable JSON into " + filePath, e);
    }
  }
}
