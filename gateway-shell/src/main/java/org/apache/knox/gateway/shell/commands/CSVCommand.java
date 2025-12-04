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
package org.apache.knox.gateway.shell.commands;

import java.io.IOException;
import java.util.List;

import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.groovy.groovysh.Groovysh;

public class CSVCommand extends AbstractKnoxShellCommand {
  private static final String USAGE = ":csv [withHeaders] file-url||$variable-name [assign resulting-variable-name]";
  private static final String DESC = "Build table from CSV file located at provided URL or KnoxShell $variable-name";
  private boolean withHeaders;
  private String url;

  public CSVCommand(Groovysh shell) {
    super(shell, ":CSV", ":csv", DESC, USAGE, DESC);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object execute(List<String> args) {
    KnoxShellTable table = null;
    String bindVariableName = null;
    if (!args.isEmpty()) {
      bindVariableName = getBindingVariableNameForResultingTable(args);
    }
    if (args.get(0).contentEquals("withHeaders")) {
      withHeaders = true;
      url = args.get(1);
    }
    else {
      url = args.get(0);
    }

    try {
      if (withHeaders) {
        if (url.startsWith("$")) {
          // a knoxshell variable is a csv file as a string
          String csvString = (String) getVariables().get(url.substring(1));
          table = KnoxShellTable.builder().csv().withHeaders().string(csvString);
        }
        else {
          table = KnoxShellTable.builder().csv().withHeaders().url(url);
        }
      }
      else {
        if (url.startsWith("$")) {
          // a knoxshell variable is a csv file as a string
          String csvString = (String) getVariables().get(url.substring(1));
          table = KnoxShellTable.builder().csv().string(csvString);
        }
        else {
          table = KnoxShellTable.builder().csv().url(url);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (table != null && bindVariableName != null) {
      getVariables().put(bindVariableName, table);
    }
    return table;
  }

}
