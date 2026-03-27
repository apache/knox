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
package org.apache.knox.gateway.shell;
import org.jline.console.CommandRegistry;
import org.jline.console.CommandMethods;
import org.jline.console.CommandInput;
import org.jline.console.CmdDesc;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.SystemCompleter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleCommandRegistry implements CommandRegistry {

    private final Map<String, CommandMethods> commands;
    private final Map<String, String> aliases;

    public SimpleCommandRegistry(Map<String, CommandMethods> commands, Map<String, String> aliases) {
        this.commands = commands;
        this.aliases = aliases != null ? aliases : Collections.emptyMap();
    }

    @Override
    public boolean hasCommand(String command) {
        return commands.containsKey(command) || aliases.containsKey(command);
    }

    @Override
    public Set<String> commandNames() {
        return commands.keySet();
    }

    @Override
    public Map<String, String> commandAliases() {
        return aliases;
    }

    @Override
    public List<String> commandInfo(String command) {
        return Collections.emptyList();
    }

    @Override
    public CmdDesc commandDescription(List<String> args) {
        return new CmdDesc(false); // Disables floating tooltip widgets for these commands
    }

    @Override
    public SystemCompleter compileCompleters() {
        SystemCompleter out = new SystemCompleter();

        // Add all our main commands to the JLine completion engine
        for (String cmd : commands.keySet()) {
            out.add(cmd, getCompletersForCommand(cmd));
        }

        // Tell JLine to wire up all shortcuts to the exact same completion logic
        out.addAliases(aliases);
        return out;
    }

    @Override
    public Object invoke(CommandSession session, String command, Object... args) throws Exception {
        // Resolve shortcut to full command, or keep as-is
        String actualCommand = aliases.getOrDefault(command, command);
        CommandMethods methods = commands.get(actualCommand);

        if (methods != null && methods.execute() != null) {
            CommandInput input = new CommandInput(command, args, session);
            return methods.execute().apply(input);
        }
        return null;
    }

    private List<Completer> getCompletersForCommand(String command) {
        String actualCommand = aliases.getOrDefault(command, command);
        CommandMethods methods = commands.get(actualCommand);

        if (methods != null && methods.compileCompleter() != null) {
            return methods.compileCompleter().apply(actualCommand);
        }
        return Collections.singletonList(NullCompleter.INSTANCE);
    }
}