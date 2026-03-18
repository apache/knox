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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.CredentialCollector;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

public abstract class AbstractKnoxShellCommand {
  static final String KNOXSQLHISTORY = "__knoxsqlhistory";
  protected static final String KNOXDATASOURCES = "__knoxdatasources";

  // NEW FIELDS: Holding the modern execution context
  protected final GroovyEngine engine;
  protected final Terminal terminal;
  private final String name;
  private final String shortcut;

  private String description;
  private String usage;
  private String help;

  // REFACTORED CONSTRUCTOR: Injects JLine 3 dependencies
  public AbstractKnoxShellCommand(GroovyEngine engine, Terminal terminal, String name, String shortcut) {
    this.engine = engine;
    this.terminal = terminal;
    this.name = name;
    this.shortcut = shortcut;
  }

  // REFACTORED CONSTRUCTOR: Overload with help docs
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

  // NEW METHODS: Exposing the command identifiers since CommandSupport is gone
  public String getName() { return name; }
  public String getShortcut() { return shortcut; }

  public String getDescription() {
    return description;
  }

  public String getUsage() {
    return usage;
  }

  public String getHelp() {
    return help;
  }

  // NEW ABSTRACT METHOD: Enforces the execution contract for subclasses
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
    LineReader reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .build();

    String collectedUsername;
    char[] collectedPassword;

    try {
      // 1. Prompt for Username in clear text
      collectedUsername = reader.readLine("Username: ");
      if (collectedUsername == null || collectedUsername.trim().isEmpty()) {
        throw new CredentialCollectionException("Login cancelled: Username cannot be empty.");
      }

      // 2. Prompt for Password using the '*' mask character
      String passStr = reader.readLine("Password: ", '*');
      collectedPassword = (passStr != null) ? passStr.toCharArray() : new char[0];

    } catch (org.jline.reader.UserInterruptException e) {
      throw new CredentialCollectionException("Login cancelled by user (Ctrl+C).");
    } catch (Exception e) {
      throw new CredentialCollectionException("Failed to read credentials from terminal", e);
    }

    // 3. Return an anonymous implementation of CredentialCollector
    // so we don't break the contract expected by child classes
    return new CredentialCollector() {
      @Override
      public void collect() throws CredentialCollectionException {
        // We already collected the credentials in the parent method,
        // so this can safely remain a no-op if child classes call it again.
      }

      @Override
      public String name() {
        return collectedUsername;
      }

      @Override
      public char[] chars() {
        return collectedPassword;
      }

      @Override
      public String string() {
        return new String(collectedPassword);
      }

      @Override
      public byte[] bytes() {
        return new String(collectedPassword).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      }
      @Override
      public String type() {
        return "";
      }

      @Override
      public void setPrompt(String prompt) {

      }

      @Override
      public void setName(String name) {

      }
    };
  }
}