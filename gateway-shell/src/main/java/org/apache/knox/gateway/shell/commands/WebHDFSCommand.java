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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.CredentialCollector;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.KnoxShellException;
import org.apache.knox.gateway.shell.hdfs.Hdfs;
import org.apache.knox.gateway.shell.hdfs.Status.Response;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.knox.gateway.util.JsonUtils;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class WebHDFSCommand extends AbstractKnoxShellCommand {
  private static final String DESC = "POSIX style commands for Hadoop Filesystems";
  private static final String USAGE = "Usage: \n" +
  "  :fs mounts \n" +
  "  :fs mount target-topology-url mountpoint-name \n" +
  "  :fs unmount mountpoint-name \n" +
  "  :fs ls {target-path} \n" +
  "  :fs cat {target-path} \n" +
  "  :fs get {from-path} {to-path} \n" +
  "  :fs put {from-path} {to-path} \n" +
  "  :fs rm {target-path} \n" +
  "  :fs mkdir {dir-path} \n";

  private Map<String, KnoxSession> sessions = new HashMap<>();

  public WebHDFSCommand(GroovyEngine engine, Terminal terminal) {
    super(engine, terminal, ":filesystem", ":fs", DESC, USAGE, DESC);
  }

  @Override
  public Object execute(List<String> args) {
    Map<String, String> mounts = getMountPoints();
    if (mounts == null) {
      mounts = new HashMap<>();
    }

    String action = (args == null || args.isEmpty()) ? "ls" : args.get(0);

    if ("mount".equalsIgnoreCase(action)) {
      if (args.size() < 3) return printError("Usage: :fs mount <target-topology-url> <mountpoint-name>");
      return mount(mounts, args.get(1), args.get(2));
    }
    else if ("unmount".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs unmount <mountpoint-name>");
      unmount(mounts, args.get(1));
      return "Unmounted " + args.get(1);
    }
    else if ("mounts".equalsIgnoreCase(action)) {
      return listMounts(mounts);
    }
    else if ("ls".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs ls <target-path>");
      return listStatus(mounts, args.get(1));
    }
    else if ("put".equalsIgnoreCase(action)) {
      if (args.size() < 3) return printError("Usage: :fs put <from-path> <to-path> [permissions]");
      String localFile = args.get(1);
      String path = args.get(2);
      int permission = 755;
      if (args.size() >= 4) {
        try {
          permission = Integer.parseInt(args.get(3));
        } catch (NumberFormatException e) {
          return printError("Invalid permission format. Expected integer.");
        }
      }
      return put(mounts, localFile, path, permission);
    }
    else if ("rm".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs rm <target-path>");
      return remove(mounts, args.get(1));
    }
    else if ("cat".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs cat <target-path>");
      return cat(mounts, args.get(1));
    }
    else if ("mkdir".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs mkdir <target-path> [perms]");
      String perms = (args.size() == 3) ? args.get(2) : null;
      return mkdir(mounts, args.get(1), perms);
    }
    else if ("get".equalsIgnoreCase(action)) {
      if (args.size() < 2) return printError("Usage: :fs get <from-path> [to-path]");
      String path = args.get(1);
      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);

      if (session != null) {
        String from = determineTargetPath(path, mountPoint);
        String to = (args.size() > 2) ? args.get(2) :
        System.getProperty("user.home") + File.separator + getFileName(path);
        return get(mountPoint, from, to);
      } else {
        return "No session established for mountPoint: " + mountPoint + ". Use :fs mount {topology-url} {mountpoint-name}";
      }
    } else {
      terminal.writer().println("Unknown filesystem command: " + action);
      terminal.writer().println(getUsage());
      terminal.writer().flush();
    }
    return "";
  }

  private String printError(String msg) {
    terminal.writer().println("Error: " + msg);
    terminal.writer().flush();
    return null;
  }

  // HELPER to safely extract filename
  private String getFileName(String path) {
    int index = path.lastIndexOf(File.separator);
    return (index > -1) ? path.substring(index) : path;
  }

  private String get(String mountPoint, String from, String to) {
    try {
      Hdfs.get(sessions.get(mountPoint)).from(from).file(to).now().getString();
      return "Successfully copied: " + from + " to: " + to;
    } catch (KnoxShellException | IOException e) {
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
      return "Exception occurred: " + e.getMessage();
    }
  }

  private String mkdir(Map<String, String> mounts, String path, String perms) {
    String mountPoint = determineMountPoint(path);
    KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
    if (session != null) {
      String targetPath = determineTargetPath(path, mountPoint);
      if (!exists(session, targetPath)) {
        try {
          if (perms != null) {
            Hdfs.mkdir(sessions.get(mountPoint)).dir(targetPath).perm(perms).now().getString();
          } else {
            Hdfs.mkdir(session).dir(targetPath).now().getString();
          }
          return "Successfully created directory: " + targetPath;
        } catch (KnoxShellException | IOException e) {
          e.printStackTrace(terminal.writer());
          terminal.writer().flush();
          return "Exception occurred: " + e.getMessage();
        }
      } else {
        return targetPath + " already exists";
      }
    }
    return "No session established for mountPoint: " + mountPoint;
  }

  private String cat(Map<String, String> mounts, String path) {
    String mountPoint = determineMountPoint(path);
    KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
    if (session != null) {
      String targetPath = determineTargetPath(path, mountPoint);
      try {
        return Hdfs.get(session).from(targetPath).now().getString();
      } catch (KnoxShellException | IOException e) {
        e.printStackTrace(terminal.writer());
        terminal.writer().flush();
        return "Exception occurred: " + e.getMessage();
      }
    }
    return "No session established for mountPoint: " + mountPoint;
  }

  private String remove(Map<String, String> mounts, String path) {
    String mountPoint = determineMountPoint(path);
    KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
    if (session != null) {
      String targetPath = determineTargetPath(path, mountPoint);
      try {
        Hdfs.rm(session).file(targetPath).now().getString();
      } catch (KnoxShellException | IOException e) {
        e.printStackTrace(terminal.writer());
        terminal.writer().flush();
      }
    } else {
      return "No session established for mountPoint: " + mountPoint;
    }
    return "Successfully removed: " + path;
  }

  private String put(Map<String, String> mounts, String localFile, String path, int permission) {
    String mountPoint = determineMountPoint(path);
    KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
    if (session != null) {
      String targetPath = determineTargetPath(path, mountPoint);
      try {
        boolean overwrite = false;
        if (exists(session, targetPath)) {
          //Replaced System.console() with JLine 3 input
          String answer = collectClearInput(targetPath + " already exists. Would you like to overwrite? (Y/n): ");
          if (answer != null && answer.trim().equalsIgnoreCase("y")) {
            overwrite = true;
          } else {
            return "Put operation cancelled.";
          }
        }
        Hdfs.put(session).file(localFile).to(targetPath).overwrite(overwrite).permission(permission).now().getString();
      } catch (IOException e) {
        e.printStackTrace(terminal.writer());
        terminal.writer().flush();
        return "Exception occurred: " + e.getMessage();
      }
    } else {
      return "No session established for mountPoint: " + mountPoint;
    }
    return "Successfully put: " + localFile + " to: " + path;
  }

  private boolean exists(KnoxSession session, String path) {
    try {
      Response response = Hdfs.status(session).file(path).now();
      return response.exists();
    } catch (KnoxShellException e) {
      return false;
    }
  }

  private Object listStatus(Map<String, String> mounts, String path) {
    try {
      String mountPoint = determineMountPoint(path);
      if (mountPoint != null) {
        KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
        if (session != null) {
          String directory = determineTargetPath(path, mountPoint);
          String json = Hdfs.ls(session).dir(directory).now().getString();

          Map<String, HashMap<String, ArrayList<HashMap<String, String>>>> map = JsonUtils.getFileStatusesAsMap(json);
          if (map != null && map.containsKey("FileStatuses")) {
            ArrayList<HashMap<String, String>> list = map.get("FileStatuses").get("FileStatus");
            return buildTableFromListStatus(directory, list);
          }
        } else {
          return "No session established for mountPoint: " + mountPoint;
        }
      } else {
        return "No mountpoint found. Use ':fs mount {topologyURL} {mountpoint}'.";
      }
    } catch (KnoxShellException | IOException e) {
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
      return "Exception occurred: " + e.getMessage();
    }
    return null;
  }

  private KnoxShellTable listMounts(Map<String, String> mounts) {
    KnoxShellTable table = new KnoxShellTable();
    table.header("Mount Point").header("Topology URL");
    for (Map.Entry<String, String> entry : mounts.entrySet()) {
      table.row().value(entry.getKey()).value(entry.getValue());
    }
    return table;
  }

  private void unmount(Map<String, String> mounts, String mountPoint) {
    sessions.remove(mountPoint);
    mounts.remove(mountPoint);
    KnoxSession.persistMountPoints(mounts);
  }

  private String mount(Map<String, String> mounts, String url, String mountPoint) {
    KnoxSession session = establishSession(mountPoint, url);
    if (session != null) {
      mounts.put(mountPoint, url);
      KnoxSession.persistMountPoints(mounts);
      return url + " mounted as " + mountPoint;
    }
    return "Failed to mount " + url + " as " + mountPoint;
  }

  private KnoxSession getSessionForMountPoint(Map<String, String> mounts, String mountPoint) {
    KnoxSession session = sessions.get(mountPoint);
    if (session == null) {
      String url = mounts.get(mountPoint);
      if (url != null) {
        session = establishSession(mountPoint, url);
      }
    }
    return session;
  }

  private KnoxSession establishSession(String mountPoint, String url) {
    CredentialCollector dlg;
    try {
      dlg = login();
    } catch (CredentialCollectionException e) {
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
      return null;
    }
    String username = dlg.name();
    String password = new String(dlg.chars());
    try {
      KnoxSession session = KnoxSession.login(url, username, password);
      sessions.put(mountPoint, session);
      return session;
    } catch (URISyntaxException e) {
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
      return null;
    }
  }

  // Safely prompt for input using JLine 3
  private String collectClearInput(String prompt) {
    try {
      LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
      return reader.readLine(prompt);
    } catch (Exception e) {
      return ""; // Fallback gracefully if interrupted
    }
  }

  private String determineTargetPath(String path, String mountPoint) {
    String directory = null;
    if (path.startsWith("/")) {
      directory = stripMountPoint(path, mountPoint);
    }
    return directory;
  }

  private String stripMountPoint(String path, String mountPoint) {
    String newPath = path.replace("/" + mountPoint, "");
    return newPath;
  }

  private String determineMountPoint(String path) {
    if (path != null && path.startsWith("/")) {
      String[] pathElements = path.split("/");
      // Prevent array bounds exception
      if (pathElements.length > 1) {
        return pathElements[1];
      }
    }
    return null;
  }

  private KnoxShellTable buildTableFromListStatus(String directory, List<HashMap<String, String>> list) {
    Calendar cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
    KnoxShellTable table = new KnoxShellTable();
    table.title(directory);
    table.header("permission")
      .header("owner")
      .header("group")
      .header("length")
      .header("modtime")
      .header("name");

    if (list != null) {
      for (Map<String, String> map : list) {
        cal.setTimeInMillis(Long.parseLong(map.get("modificationTime")));
        table.row()
        .value(map.get("permission"))
        .value(map.get("owner"))
        .value(map.get("group"))
        .value(map.get("length"))
        .value(cal.getTime())
        .value(map.get("pathSuffix"));
      }
    }
    return table;
  }

  protected Map<String, String> getMountPoints() {
    try {
      return KnoxSession.loadMountPoints();
    } catch (IOException e) {
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }
    return null;
  }

  public static void main(String[] args) {
    try {
      Terminal terminal = TerminalBuilder.builder().system(true).build();
      GroovyEngine engine = new GroovyEngine();
      WebHDFSCommand cmd = new WebHDFSCommand(engine, terminal);
      Object result = cmd.execute(new ArrayList<>(Arrays.asList(args)));
      if (result != null) {
        terminal.writer().println(result);
        terminal.writer().flush();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
