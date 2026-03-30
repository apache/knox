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

import groovy.ui.GroovyMain;

import org.apache.groovy.groovysh.jline.SystemRegistryImpl;
import org.apache.knox.gateway.shell.commands.AbstractKnoxShellCommand;
import org.apache.knox.gateway.shell.commands.CSVCommand;
import org.apache.knox.gateway.shell.commands.DataSourceCommand;
import org.apache.knox.gateway.shell.commands.ImportCommand;
import org.apache.knox.gateway.shell.commands.LoadCommand;
import org.apache.knox.gateway.shell.commands.SelectCommand;
import org.apache.knox.gateway.shell.commands.ShowCommand;
import org.apache.knox.gateway.shell.commands.WebHDFSCommand;
import org.apache.knox.gateway.shell.hbase.HBase;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.apache.knox.gateway.shell.job.Job;
import org.apache.knox.gateway.shell.manager.Manager;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.knox.gateway.shell.workflow.Workflow;
import org.apache.knox.gateway.shell.yarn.Yarn;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.console.CommandMethods;
import org.jline.console.SystemRegistry;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Shell {

  private static final List<String> NON_INTERACTIVE_COMMANDS = Arrays.asList("buildTrustStore", "init", "list", "destroy", "knoxline");

  private static final List<String> EXIT_COMMANDS = Arrays.asList(":exit", ":x", ":quit", ":q");

  private static final List<String> HELP_COMMANDS = Arrays.asList(":help", ":h", "?");

  private static final String[] IMPORTS = new String[] {
      KnoxSession.class.getName(),
      HBase.class.getName(),
      Hdfs.class.getName(),
      Job.class.getName(),
      Workflow.class.getName(),
      Yarn.class.getName(),
      TimeUnit.class.getName(),
      Manager.class.getName(),
      KnoxShellTable.class.getName()
  };

  @SuppressWarnings("PMD.DoNotUseThreads")
  public static void main(String... args) throws Exception {
    if (args.length > 0) {
      if (NON_INTERACTIVE_COMMANDS.contains(args[0])) {
        final String[] arguments = new String[args.length == 1 ? 1 : 3];
        arguments[0] = args[0];
        if (args.length > 1) {
          arguments[1] = "--gateway";
          arguments[2] = args[1];
        }
        KnoxSh.main(arguments);
      } else {
        // Execute Groovy scripts headlessly
        GroovyMain.main(args);
      }
    } else {
      // Boot the Interactive JLine 3 REPL
      startInteractiveShell();
    }
  }

  private static void startInteractiveShell() throws Exception {
    // 1. Build Terminal and Engine
    Terminal terminal = TerminalBuilder.builder().system(true).name("KnoxShell").build();
    GroovyEngine engine = new GroovyEngine();

    // 2. Pre-load Knox imports
    ImportCommand importCmd = new ImportCommand(engine, terminal);
    for (String name : IMPORTS) {
      engine.execute("import " + name);
      importCmd.registerBuiltIn(name);
    }

    // 3. Instantiate and Map Custom Commands
    Map<String, AbstractKnoxShellCommand> registry = new HashMap<>();
    SelectCommand selectCmd = new SelectCommand(engine, terminal);
    DataSourceCommand dsCmd = new DataSourceCommand(engine, terminal);
    CSVCommand csvCmd = new CSVCommand(engine, terminal);
    WebHDFSCommand hdfsCmd = new WebHDFSCommand(engine, terminal);
    ShowCommand showCmd = new ShowCommand(engine, terminal, importCmd);
    LoadCommand loadCmd = new LoadCommand(engine, terminal);

    registerCommand(registry, importCmd);
    registerCommand(registry, selectCmd);
    registerCommand(registry, dsCmd);
    registerCommand(registry, csvCmd);
    registerCommand(registry, hdfsCmd);
    registerCommand(registry, showCmd);
    registerCommand(registry, loadCmd);

    Map<String, CommandMethods> commandMethods = new HashMap<>();
    Map<String, String> commandAliases = new HashMap<>();


    registry.forEach((rawName, cmd) -> {

      // 1. THE FIX: Force the JLine registry to know the commands start with a colon
      String name = rawName.startsWith(":") ? rawName : ":" + rawName;

      String rawShortcut = cmd.getShortcut();
      if (rawShortcut != null && !rawShortcut.isEmpty()) {
        String shortcut = rawShortcut.startsWith(":") ? rawShortcut : ":" + rawShortcut;
        commandAliases.put(shortcut, name);
      }

      // 2. Put them in the map with the colon-prefixed names
      commandMethods.put(name, new CommandMethods(
      (input) -> {
        try {
          String[] allTokens = input.args();
          // input.args() includes the command name, so we skip(1) to get the arguments
          List<String> argsList = (allTokens != null && allTokens.length > 1)
          ? Arrays.stream(allTokens).skip(1).collect(Collectors.toList())
          : Collections.emptyList();

          return cmd.execute(argsList);
        } catch (Exception e) {
          input.session().terminal().writer().println("Error: " + e.getMessage());
          return null;
        }
      },
      (line) -> {
        List<Completer> completers = cmd.getCompleters();
        return (completers != null && !completers.isEmpty())
        ? completers
        : Collections.singletonList(NullCompleter.INSTANCE);
      }
      ));
    });

    DefaultParser parser = new DefaultParser();
    Path workDir = Paths.get(System.getProperty("user.dir"));
    SimpleCommandRegistry knoxRegistry = new SimpleCommandRegistry(commandMethods, commandAliases);
    SystemRegistry systemRegistry = new SystemRegistryImpl(parser, terminal, () -> workDir, null);
    systemRegistry.setCommandRegistries(knoxRegistry);
    SystemRegistry.add(systemRegistry);

    // 4. Setup Tab Completers
    // StringsCompleter automatically suggests our custom commands (e.g., ":sql", ":fs")

    Completer combinedCompleter = new AggregateCompleter(
      systemRegistry.completer(),
      engine.getScriptCompleter()
    );

    // 5. Build the LineReader
    LineReader reader = LineReaderBuilder.builder()
    .terminal(terminal)
    .completer(combinedCompleter)
    .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".knoxshell_history"))
    .build();

    terminal.writer().println("Apache Knox Shell");
    terminal.writer().println("Type ':help' for help, ':exit' or ':quit' (':x' or ':q') to quit.");
    terminal.writer().flush();

    // 6. Setup Shutdown Hook (Calling closeConnections directly on our object instances)
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("\nClosing any open connections...");
      dsCmd.closeConnections();
      selectCmd.closeConnections();
    }));


    // 7. The REPL Loop
    while (true) {
      try {
        String line = reader.readLine("knox> ");
        if (line == null) {
          break;
        }

        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
          continue;
        }

        // --- BUILT-IN COMMANDS ---
        if (EXIT_COMMANDS.stream().anyMatch(trimmed::equalsIgnoreCase)) {
          break;
        }

        if (HELP_COMMANDS.stream().anyMatch(h -> trimmed.equalsIgnoreCase(h) || trimmed.startsWith(h + " "))) {
          String[] helpParts = trimmed.split("\\s+");

          if (helpParts.length > 1) {
            // Detailed help for a specific command (e.g., ":help :fs")
            String targetCmd = helpParts[1];
            if (registry.containsKey(targetCmd)) {
              AbstractKnoxShellCommand cmd = registry.get(targetCmd);
              terminal.writer().println(cmd.getDescription());
              terminal.writer().println(cmd.getUsage());
            } else {
              terminal.writer().println("Unknown command: " + targetCmd);
            }
          } else {
            // General help menu
            terminal.writer().println("Available Custom Knox Commands:");

            // Use a Stream to get distinct commands (ignores duplicate alias keys)
            registry.values().stream().distinct().forEach(cmd -> {
              String names = cmd.getName() + (cmd.getShortcut() != null ? ", " + cmd.getShortcut() : "");
              String desc = cmd.getDescription() != null ? cmd.getDescription() : "";
              terminal.writer().printf(Locale.ROOT, "  %-25s %s%n", names, desc);
            });

            terminal.writer().println();
            terminal.writer().printf(Locale.ROOT, "  %-25s %s%n", ":help, :h, ?", "Displays this help message or specific command usage");
            terminal.writer().printf(Locale.ROOT, "  %-25s %s%n", ":exit, :x, :quit, :q", "Exits the shell");
            terminal.writer().println("\nNote: Any other input is evaluated natively as Groovy code.");
          }
          terminal.writer().flush();
          continue; // Skip the rest of the loop
        }

        // Route custom Knox commands
        String[] parts = trimmed.split("\\s+");
        String commandName = parts[0];

        if (registry.containsKey(commandName)) {
          AbstractKnoxShellCommand cmd = registry.get(commandName);

          // Extract arguments to pass to the command
          List<String> cmdArgs = new ArrayList<>();
          if (parts.length > 1) {
            cmdArgs.addAll(Arrays.asList(parts).subList(1, parts.length));
          }

          Object res = cmd.execute(cmdArgs);
          if (res != null) {
            terminal.writer().println(res.toString());
          }
        } else {
          // Fallback to evaluating standard Groovy script logic
          Object result = engine.execute(line);
          if (result != null) {
            terminal.writer().println("==> " + result);
          }
        }

        terminal.writer().flush();

      } catch (UserInterruptException | EndOfFileException e) {
        // Ctrl+C or Ctrl+D cleanly exits the shell
        break;
      } catch (Exception e) {
        terminal.writer().println("Error: " + e.getMessage());
        terminal.writer().flush();
      }
    }
  }

  private static void registerCommand(Map<String, AbstractKnoxShellCommand> registry, AbstractKnoxShellCommand cmd) {
    registry.put(cmd.getName(), cmd);
    if (cmd.getShortcut() != null && !cmd.getShortcut().isEmpty()) {
      registry.put(cmd.getShortcut(), cmd);
    }
  }
}
