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

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shows variables, classes or imports in the current shell session.
 * <p>
 * Usage:
 *   :show              - lists all variables (default)
 *   :show variables    - lists all variables
 *   :show imports      - lists active imports
 *   :show all          - lists both variables and imports
 */
public class ShowCommand extends AbstractKnoxShellCommand {

    private static final String NAME     = ":show";
    private static final String SHORTCUT = ":S";
    private static final String DESC     = "Show variables, imports or both";
    private static final String USAGE    = "Usage: :show [variables|imports|all]";
    private static final String HELP     = USAGE + "\n"
    + "  variables  - list all bound variables (default)\n"
    + "  imports    - list active import statements\n"
    + "  all        - list both variables and imports";

    public ShowCommand(GroovyEngine engine, Terminal terminal) {
        super(engine, terminal, NAME, SHORTCUT, DESC, USAGE, HELP);
    }

    @Override
    public Object execute(List<String> args) {
        String what = (args == null || args.isEmpty()) ? "variables" : args.get(0).toLowerCase(Locale.ROOT);

        switch (what) {
            case "variables":
                showVariables();
                break;
            case "imports":
                showImports();
                break;
            case "all":
                showVariables();
                terminal.writer().println();
                showImports();
                break;
            default:
                terminal.writer().println(USAGE);
                break;
        }

        terminal.writer().flush();
        return null;
    }

    private void showVariables() {
        Map<String, Object> variables = engine.find();
        if (variables == null || variables.isEmpty()) {
            terminal.writer().println("No variables defined.");
            return;
        }

        terminal.writer().println("Variables:");
        variables.forEach((name, value) -> {
            String type = (value != null) ? value.getClass().getSimpleName() : "null";
            String display = (value != null) ? value.toString() : "null";
            terminal.writer().printf(Locale.ROOT, "  %-25s (%s) = %s%n", name, type, display);
        });
    }

    private void showImports() {
        java.util.Set<String> imports = engine.getImports().keySet();
        if (imports.isEmpty()) {
            terminal.writer().println("No imports registered.");
        } else {
            terminal.writer().println("Imports:");
            imports.forEach(i -> terminal.writer().println("  import " + i));
        }
    }

    @Override
    public List<Completer> getCompleters() {
        Completer subCommandCompleter = new StringsCompleter("variables", "imports", "all");
        return Arrays.asList(subCommandCompleter, NullCompleter.INSTANCE);
    }

}
