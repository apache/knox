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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class JSONKnoxShellTableBuilder extends KnoxShellTableBuilder {

  JSONKnoxShellTableBuilder(KnoxShellTable table) {
    super(table);
  }

  public KnoxShellTable fromJson(String json) throws IOException {
    return toKnoxShellTable(json);
  }

  // introduced a private method so that it can be invoked from both public ones and AspectJ will not intercept it
  private KnoxShellTable toKnoxShellTable(String json) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new JsonFactory());
    final SimpleModule module = new SimpleModule();
    module.addDeserializer(KnoxShellTable.class, new KnoxShellTableRowDeserializer(table));
    mapper.registerModule(module);

    final KnoxShellTable tableFromJson = mapper.readValue(json, new TypeReference<KnoxShellTable>() {
    });
    if (title != null) {
      tableFromJson.title(title);
    }
    return tableFromJson;
  }

  public KnoxShellTable path(String path) throws IOException {
    return toKnoxShellTable(FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8));
  }
}
