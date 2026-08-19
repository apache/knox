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

import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.KnoxSession;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class LoginCommand extends AbstractKnoxShellCommand {

  public LoginCommand(GroovyEngine engine, Terminal terminal) {
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
      KnoxLoginDialog dlg = new KnoxLoginDialog();
      dlg.collect();
      if (dlg.ok) {
        session = KnoxSession.login(url, dlg.username, new String(dlg.pass));
        engine.put("__knoxsession", session);
        terminal.writer().println("Session established for: " + url);
        terminal.writer().flush();
      } else {
        terminal.writer().println("Login cancelled.");
        terminal.writer().flush();
      }
    } catch (CredentialCollectionException | URISyntaxException e) {
      terminal.writer().println("Failed to establish session: " + e.getMessage());
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }

    return session;
  }

  public static void main(String[] args) {
    try {
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
