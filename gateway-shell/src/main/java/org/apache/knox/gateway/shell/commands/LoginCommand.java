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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.knox.gateway.shell.KnoxSession;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class LoginCommand extends AbstractKnoxShellCommand {

  public LoginCommand(GroovyEngine engine, Terminal terminal) {
    // Pass identifiers and docs up to AbstractKnoxShellCommand
    super(engine, terminal, ":login", ":lgn",
    "Establishes a Knox session",
    "Usage: :login <url>",
    "Establishes a Knox session using terminal credentials");
  }

  @Override
  public Object execute(List<String> args) {
    if (args == null || args.isEmpty()) {
      terminal.writer().println("Error: Knox Gateway URL required.");
      terminal.writer().println(getUsage());
      terminal.writer().flush();
      return null;
    }

    String url = args.get(0);
    KnoxSession session = null;

    try {
      LineReader reader = LineReaderBuilder.builder()
      .terminal(terminal)
      .build();

      // 1. Prompt for Username (Clear text)
      String username = reader.readLine("Username: ");
      if (username == null || username.trim().isEmpty()) {
        terminal.writer().println("Login cancelled: Username cannot be empty.");
        terminal.writer().flush();
        return null;
      }

      // 2. Prompt for Password (Masked with '*')
      // JLine 3 intercepts keystrokes and prints the mask char instead of the actual key
      String password = reader.readLine("Password: ", '*');

      if (password != null) {
        // Create the session
        session = KnoxSession.login(url, username, password);

        // Inject the session into the Groovy 5 environment
        engine.put("__knoxsession", session);

        terminal.writer().println("Session established for: " + url);
        terminal.writer().flush();
      } else {
        terminal.writer().println("Login cancelled.");
        terminal.writer().flush();
      }

    } catch (URISyntaxException e) {
      terminal.writer().println("Invalid URL syntax: " + e.getMessage());
      terminal.writer().flush();
    } catch (Exception e) {
      terminal.writer().println("Failed to establish session: " + e.getMessage());
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }

    return session; // Returning the session object
  }

  public static void main(String[] args) {
    try {
      // Test using JLine 3 Terminal
      Terminal terminal = TerminalBuilder.builder().system(true).build();
      GroovyEngine engine = new GroovyEngine();
      LoginCommand cmd = new LoginCommand(engine, terminal);

      List<String> args2 = new ArrayList<>();
      args2.add("https://localhost:8443/gateway/sandbox");
      cmd.execute(args2);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}