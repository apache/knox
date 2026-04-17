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
    + "  variables  - purge user variables, keep internal Knox state\n"
    + "  imports    - purge user-added imports, keep built-in Knox imports\n"
    + "  all        - purge both variables and user imports";

    /** Prefix used by Knox internal bindings (__knoxdatasource, __knoxsession, etc.) */
    private static final String KNOX_INTERNAL_PREFIX = "__knox";

    /**
     * @param engine        the GroovyEngine
     * @param terminal      the JLine terminal
     */
    public PurgeCommand(GroovyEngine engine, Terminal terminal) {
        super(engine, terminal, NAME, SHORTCUT, DESC, USAGE, HELP);
    }

    @Override
    public Object execute(List<String> args) {
        String what = (args == null || args.isEmpty()) ? "variables" : args.get(0).toLowerCase(Locale.ROOT);

        switch (what) {
            case "variables":
                int varCount = clearVariables();
                terminal.writer().println("Purged " + varCount + " variable(s). Internal Knox state preserved.");
                break;
            case "imports":
                int importCount = clearImports();
                terminal.writer().println("Purged " + importCount + " import(s).");
                break;
            case "all":
                int vc = clearVariables();
                int ic = clearImports();
                terminal.writer().println("Purged " + vc + " variable(s) and " + ic + " import(s).");
                break;
            default:
                terminal.writer().println(USAGE);
                break;
        }

        terminal.writer().flush();
        return null;
    }

    private int clearVariables() {
        java.util.Map<String, Object> variables = engine.find();
        if (variables == null || variables.isEmpty()) {
            return 0;
        }

        int count = 0;
        List<String> keysToDelete = new java.util.ArrayList<>();
        for (String variableName : variables.keySet()) {
            // Preserve internal Knox bindings
            if (variableName != null && !variableName.startsWith(KNOX_INTERNAL_PREFIX)) {
                keysToDelete.add(variableName);
                count++;
            }
        }
        if (!keysToDelete.isEmpty()) {
            engine.del(keysToDelete.toArray(new String[0]));
        }
        return count;
    }

    private int clearImports() {
        Map<String, String> imports = engine.getImports();

        if (imports == null || imports.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String importName : imports.keySet()) {
            engine.removeImport(importName);
            count++;
        }
        return count;
    }

    @Override
    public List<Completer> getCompleters() {
        Completer subCommandCompleter = new StringsCompleter("variables", "imports", "all");
        return Arrays.asList(subCommandCompleter, NullCompleter.INSTANCE);
    }
}
