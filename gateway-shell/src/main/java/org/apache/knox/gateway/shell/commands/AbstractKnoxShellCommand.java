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

import java.util.Collections;
import java.util.List;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.CredentialCollector;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.terminal.Terminal;

public abstract class AbstractKnoxShellCommand {

  protected final GroovyEngine engine;
  protected final Terminal terminal;
  private final String name;
  private final String shortcut;

  private String description;
  private String usage;
  private String help;

  public AbstractKnoxShellCommand(GroovyEngine engine, Terminal terminal, String name, String shortcut,
                                  String desc, String usage, String help) {
    this.engine = engine;
    this.terminal = terminal;
    this.name = name;
    this.shortcut = shortcut;
    this.description = desc;
    this.usage = usage;
    this.help = help;
  }

  public String getName() {
    return name;
  }

  public String getShortcut() {
    return shortcut;
  }

  public String getDescription() {
    return description;
  }

  public String getUsage() {
    return usage;
  }

  public String getHelp() {
    return help;
  }

  public List<Completer> getCompleters() {
    return Collections.singletonList(NullCompleter.INSTANCE);
  }

  public abstract Object execute(List<String> args) throws Exception;

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

  protected CredentialCollector login() throws CredentialCollectionException {
    KnoxLoginDialog dlg = new KnoxLoginDialog();
    dlg.collect();
    if (!dlg.ok) {
      throw new CredentialCollectionException("Login cancelled by user.");
    }
    return dlg;
  }
}
