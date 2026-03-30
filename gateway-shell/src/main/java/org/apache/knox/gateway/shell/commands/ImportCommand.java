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
import org.jline.terminal.Terminal;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages Groovy imports in the current shell session.
 * <p>
 * Usage:
 *   :import                              - lists active imports
 *   :import org.apache.knox.gateway.shell.KnoxSession   - adds a single import
 *   :import org.apache.knox.gateway.shell.*             - wildcard import
 */
public class ImportCommand extends AbstractKnoxShellCommand {

    private static final String NAME     = ":import";
    private static final String SHORTCUT = ":i";
    private static final String DESC     = "Import a class into the namespace";
    private static final String USAGE    = "Usage: :import [<fully-qualified-class-or-package.*>]\n"
    + "  :import                - list active imports\n"
    + "  :import <fqcn>         - add a new import\n"
    + "  :import <package>.*    - wildcard import";
    private static final String HELP     = USAGE;

    private final Set<String> activeImports = new LinkedHashSet<>();

    public ImportCommand(GroovyEngine engine, Terminal terminal) {
        super(engine, terminal, NAME, SHORTCUT, DESC, USAGE, HELP);
    }

    /**
     * Registers a built-in import so it appears in the :import listing.
     * Called from Shell during startup for each pre-loaded Knox import.
     */
    public void registerBuiltIn(String fqcn) {
        activeImports.add(fqcn);
    }

    /**
     * Returns an unmodifiable view of the currently active imports.
     * Used by ShowCommand to display imports via :show imports.
     */
    public Set<String> getActiveImports() {
        return Collections.unmodifiableSet(activeImports);
    }

    /**
     * Removes an import from the tracked set.
     * Note: this does not truly "unimport" from the Groovy classloader,
     * but it removes it from the tracked listing and from future :show output.
     *
     * @param fqcn the fully-qualified class or package.* to remove
     * @return true if the import was present and removed
     */
    public boolean removeImport(String fqcn) {
        return activeImports.remove(fqcn);
    }

    @Override
    public Object execute(List<String> args) {
        if (args == null || args.isEmpty()) {
            // List mode
            if (activeImports.isEmpty()) {
                terminal.writer().println("No imports registered.");
            } else {
                terminal.writer().println("Active imports:");
                activeImports.forEach(i -> terminal.writer().println("  import " + i));
            }
            terminal.writer().flush();
            return null;
        }

        // Join all args in case user typed "import java.util. *" with spaces
        String target = String.join("", args).trim();

        // Strip leading "import " if the user typed ":import import java.util.List"
        if (target.toLowerCase().startsWith("import ")) {
            target = target.substring(7).trim();
        }

        // Basic validation: must contain a dot (package separator)
        if (!target.contains(".")) {
            terminal.writer().println("Invalid import: '" + target
            + "'. Expected a fully-qualified class or package (e.g. java.util.List or java.util.*).");
            terminal.writer().flush();
            return null;
        }

        try {
            engine.execute("import " + target);
            activeImports.add(target);
            terminal.writer().println("==> import " + target);
        } catch (Exception e) {
            terminal.writer().println("Failed to import '" + target + "': " + e.getMessage());
        }

        terminal.writer().flush();
        return null;
    }
}