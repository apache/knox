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
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Clears variables, imports or both from the current shell session.
 * Internal Knox variables (prefixed with __knox) are preserved by default.
 * <p>
 * Usage:
 * <ul>
 * <li>:purge                - clears user variables (preserves internal Knox state)</li>
 * <li>:purge variables      - same as above</li>
 * <li>:purge imports        - clears user-added imports (preserves built-in Knox imports)</li>
 * <li>:purge all            - clears both variables and user imports</li>
 * </ul>
 * </p>
 */
public class PurgeCommand extends AbstractKnoxShellCommand {

    private static final String NAME     = ":purge";
    private static final String SHORTCUT = ":p";
    private static final String DESC     = "Purge variables, classes, imports or preferences";
    private static final String USAGE    = "Usage: :purge [variables|imports|all]";
    private static final String HELP     = USAGE + "\n"
    + "  variables  - purge user variables, keep internal Knox state (default)\n"
    + "  imports    - purge user-added imports, keep built-in Knox imports\n"
    + "  all        - purge both variables and user imports";

    /** Prefix used by Knox internal bindings (__knoxdatasource, __knoxsession, etc.) */
    private static final String KNOX_INTERNAL_PREFIX = "__knox";

    private final ImportCommand importCommand;
    private final Set<String> builtInImports;

    /**
     * @param engine        the GroovyEngine
     * @param terminal      the JLine terminal
     * @param importCommand the ImportCommand instance (for clearing/resetting imports)
     */
    public PurgeCommand(GroovyEngine engine, Terminal terminal, ImportCommand importCommand) {
        super(engine, terminal, NAME, SHORTCUT, DESC, USAGE, HELP);
        this.importCommand = importCommand;
        // Snapshot the built-in imports at construction time so we know which ones to preserve
        this.builtInImports = Set.copyOf(importCommand.getActiveImports());
    }

    @Override
    public Object execute(List<String> args) {
        String what = (args == null || args.isEmpty()) ? "variables" : args.get(0).toLowerCase(Locale.ROOT);

        switch (what) {
            case "variables":
            case "vars":
                int varCount = clearVariables();
                terminal.writer().println("Purged " + varCount + " variable(s). Internal Knox state preserved.");
                break;
            case "imports":
                int importCount = clearUserImports();
                terminal.writer().println("Purged " + importCount + " user import(s). Built-in Knox imports preserved.");
                break;
            case "all":
                int vc = clearVariables();
                int ic = clearUserImports();
                terminal.writer().println("Purged " + vc + " variable(s) and " + ic + " user import(s).");
                break;
            default:
                terminal.writer().println(USAGE);
                break;
        }

        terminal.writer().flush();
        return null;
    }

    private int clearVariables() {
        Map<String, String> variables = engine.getVariables();
        if (variables == null || variables.isEmpty()) {
            return 0;
        }

        int count = 0;
        Iterator<Map.Entry<String, String>> it = variables.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            // Preserve internal Knox bindings
            if (!entry.getKey().startsWith(KNOX_INTERNAL_PREFIX)) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    private int clearUserImports() {
        if (importCommand == null) {
            return 0;
        }

        Set<String> current = importCommand.getActiveImports();
        // Collect user-added imports (those not in the built-in snapshot)
        List<String> toRemove = current.stream()
        .filter(imp -> !builtInImports.contains(imp))
        .toList();

        // Remove from ImportCommand's tracked set
        toRemove.forEach(importCommand::removeImport);

        return toRemove.size();
    }

    @Override
    public List<Completer> getCompleters() {
        // Index 0: command name placeholder (Shell.java handles routing)
        Completer commandPlaceholder = (reader, parsedLine, candidates) -> {};

        // Index 1: subcommands
        Completer subCommandCompleter = new StringsCompleter("variables", "imports", "all");

        // Index 2: dynamic target names (variable names for "variables", import names for "imports")
        Completer targetCompleter = (reader, parsedLine, candidates) -> {
            List<String> words = parsedLine.words();
            if (words.size() > 1) {
                String subCommand = words.get(1).toLowerCase(Locale.ROOT);
                if ("variables".equals(subCommand) || "vars".equals(subCommand)) {
                    // Suggest purgeable variable names (exclude internal __knox* ones)
                    Map<String, String> variables = engine.getVariables();
                    if (variables != null) {
                        variables.keySet().stream()
                        .filter(name -> !name.startsWith(KNOX_INTERNAL_PREFIX))
                        .forEach(name -> candidates.add(new Candidate(name)));
                    }
                } else if ("imports".equals(subCommand)) {
                    // Suggest user-added imports (exclude built-in Knox imports)
                    importCommand.getActiveImports().stream()
                    .filter(imp -> !builtInImports.contains(imp))
                    .forEach(imp -> candidates.add(new Candidate(imp)));
                }
            }
        };

        ArgumentCompleter argCompleter = new ArgumentCompleter(
            commandPlaceholder,
            subCommandCompleter,
            targetCompleter,
            NullCompleter.INSTANCE);

        return Collections.singletonList(argCompleter);
    }
}