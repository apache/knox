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
import org.jline.builtins.Completers;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.terminal.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads a Groovy script file or URL into the shell and executes it.
 * Matches the old Groovysh :load command behavior.
 * <p>
 * Usage:
 *   :load /path/to/script.groovy
 *   :load ~/scripts/setup.groovy
 *   :load https://example.com/script.groovy
 *   . /path/to/script.groovy          (alias)
 */
public class LoadCommand extends AbstractKnoxShellCommand {

  private static final String NAME     = ":load";
  private static final String SHORTCUT = ".";
  private static final String DESC     = "Load a file or URL into the buffer";
  private static final String USAGE    = "Usage: :load <file-path-or-url>\n"
      + "  :load /path/to/script.groovy\n"
      + "  :load ~/scripts/setup.groovy\n"
      + "  :load https://example.com/script.groovy\n"
      + "  . /path/to/script.groovy";
  private static final String HELP     = USAGE;

  public LoadCommand(GroovyEngine engine, Terminal terminal) {
    super(engine, terminal, NAME, SHORTCUT, DESC, USAGE, HELP);
  }

  @Override
  public Object execute(List<String> args) throws Exception {
    if (args == null || args.isEmpty()) {
      terminal.writer().println(USAGE);
      terminal.writer().flush();
      return null;
    }

    // Join args to support paths with spaces (e.g., :load /my path/script.groovy)
    String location = String.join(" ", args).trim();

    String script;
    try {
      script = readScript(location);
    } catch (Exception e) {
      terminal.writer().println("Failed to load '" + location + "': " + e.getMessage());
      terminal.writer().flush();
      return null;
    }

    if (script.isEmpty()) {
      terminal.writer().println("Warning: '" + location + "' is empty, nothing to execute.");
      terminal.writer().flush();
      return null;
    }

    terminal.writer().println("Loading " + location + " ...");
    terminal.writer().flush();

    try {
      Object result = engine.execute(script);
      if (result != null) {
        terminal.writer().println("==> " + result);
        terminal.writer().flush();
      }
      return result;
    } catch (Exception e) {
      terminal.writer().println("Error executing script: " + e.getMessage());
      terminal.writer().flush();
      return null;
    }
  }

  private String readScript(String location) throws IOException {
    // Try as URL first (http://, https://, file://)
    if (isUrl(location)) {
      return readFromUrl(location);
    }

    // Expand ~ to user home
    if (location.startsWith("~")) {
      location = System.getProperty("user.home") + location.substring(1);
    }

    Path path = Paths.get(location);
    if (!Files.exists(path)) {
      throw new IOException("File not found: " + path.toAbsolutePath());
    }
    if (!Files.isReadable(path)) {
      throw new IOException("File is not readable: " + path.toAbsolutePath());
    }
    if (Files.isDirectory(path)) {
      throw new IOException("Path is a directory, not a file: " + path.toAbsolutePath());
    }

    return Files.readString(path);
  }

  private boolean isUrl(String location) {
    return location!=null &&
    (location.startsWith("http://")
        || location.startsWith("https://")
        || location.startsWith("file://"));
  }

  private String readFromUrl(String urlStr) throws IOException {
    URL url;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e) {
      throw new IOException("Invalid URL: " + urlStr, e);
    }

    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      return reader.lines().collect(Collectors.joining("\n"));
    }
  }

  @Override
  public List<Completer> getCompleters() {
    Completers.FileNameCompleter fileNameCompleter = new Completers.FileNameCompleter();
    Completer fileCompleter = (reader, parsedLine, candidates) -> {
      String word = parsedLine.word();
      if (isUrl(word)) {
        return;
      }
      fileNameCompleter.complete(reader, parsedLine, candidates);
    };
    return Arrays.asList(fileCompleter, NullCompleter.INSTANCE);
  }

}
