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

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.apache.knox.gateway.shell.table.KnoxShellTable;

import org.jline.terminal.Terminal;

public class CSVCommand extends AbstractKnoxShellCommand {
  private static final String USAGE = ":csv [withHeaders] file-url||$variable-name [assign resulting-variable-name]";
  private static final String DESC = "Build table from CSV file located at provided URL or KnoxShell $variable-name";

  // REFACTORED CONSTRUCTOR: Takes engine and terminal instead of shell
  public CSVCommand(GroovyEngine engine, Terminal terminal) {
    super(engine, terminal, ":CSV", ":csv", DESC, USAGE, DESC);
  }

  @Override
  public Object execute(List<String> args) {
    // FIXED: Prevent shell crash if user types :csv with no arguments
    if (args == null || args.isEmpty()) {
      terminal.writer().println("Usage: " + USAGE);
      terminal.writer().flush();
      return null;
    }

    KnoxShellTable table = null;
    String bindVariableName = getBindingVariableNameForResultingTable(args);

    // FIXED: Moved to local variables to prevent state leaking between executions
    boolean withHeaders = false;
    String url;

    if ("withHeaders".equalsIgnoreCase(args.get(0))) {
      withHeaders = true;
      if (args.size() > 1) {
        url = args.get(1);
      } else {
        terminal.writer().println("Error: Missing file URL or variable name.");
        terminal.writer().flush();
        return null;
      }
    } else {
      url = args.get(0);
    }

    try {
      if (withHeaders) {
        if (url.startsWith("$")) {
          // REFACTORED: Use engine.get() instead of getVariables().get()
          String csvString = (String) engine.get(url.substring(1));
          table = KnoxShellTable.builder().csv().withHeaders().string(csvString);
        } else {
          table = KnoxShellTable.builder().csv().withHeaders().url(url);
        }
      } else {
        if (url.startsWith("$")) {
          // REFACTORED: Use engine.get() instead of getVariables().get()
          String csvString = (String) engine.get(url.substring(1));
          table = KnoxShellTable.builder().csv().string(csvString);
        } else {
          table = KnoxShellTable.builder().csv().url(url);
        }
      }
    } catch (IOException e) {
      // REFACTORED: Print errors nicely via the JLine 3 terminal
      terminal.writer().println("Error parsing CSV: " + e.getMessage());
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }

    if (table != null && bindVariableName != null) {
      // REFACTORED: Use engine.put() instead of getVariables().put()
      engine.put(bindVariableName, table);
      terminal.writer().println("Assigned resulting table to variable: " + bindVariableName);
      terminal.writer().flush();
    }

    return table;
  }
}