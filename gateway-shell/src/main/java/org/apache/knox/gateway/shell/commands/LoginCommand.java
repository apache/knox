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
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;

public class LoginCommand extends CommandSupport {

  public LoginCommand(Groovysh shell) {
    super(shell, ":login", ":lgn");
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object execute(List<String> args) {
    KnoxSession session = null;
    KnoxLoginDialog dlg = new KnoxLoginDialog();
    try {
      dlg.collect();
      if (dlg.ok) {
        session = KnoxSession.login(args.get(0), dlg.username, new String(dlg.pass));
        getVariables().put("__knoxsession", session);
      }
    } catch (CredentialCollectionException | URISyntaxException e) {
      e.printStackTrace();
    }
    return "Session established for: " + args.get(0);
  }

  public static void main(String[] args) {
    LoginCommand cmd = new LoginCommand(new Groovysh());
    List<String> args2 = new ArrayList<>();
    args2.add("https://localhost:8443/gateway");
    cmd.execute(args2);
  }
}
