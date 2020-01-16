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

import java.util.List;
import org.codehaus.groovy.tools.shell.CommandSupport;
import org.codehaus.groovy.tools.shell.Groovysh;

public abstract class AbstractKnoxShellCommand extends CommandSupport {
  static final String KNOXSQLHISTORY = "__knoxsqlhistory";
  protected static final String KNOXDATASOURCES = "__knoxdatasources";

  public AbstractKnoxShellCommand(Groovysh shell, String name, String shortcut) {
    super(shell, name, shortcut);
  }

  protected String getBindingVariableNameForResultingTable(List<String> args) {
    String variableName = null;
    boolean nextOne = false;
    for (String arg : args) {
      if (nextOne) {
        variableName = arg;
        break;
      }
      if ("assign".equalsIgnoreCase(arg)) {
        nextOne = true;
      }
    }
    return variableName;
  }
}