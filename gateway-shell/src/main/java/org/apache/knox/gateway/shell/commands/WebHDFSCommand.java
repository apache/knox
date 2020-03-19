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

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
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
import org.codehaus.groovy.tools.shell.Groovysh;

public class WebHDFSCommand extends AbstractKnoxShellCommand {
  private static final String KNOXMOUNTPOINTS = "__knoxmountpoints";
  private Map<String, KnoxSession> sessions = new HashMap<>();

  public WebHDFSCommand(Groovysh shell) {
    super(shell, ":filesystem", ":fs");
  }

  @Override
  public String getUsage() {
    String usage = "Usage: \n" +
                   "  :fs ls {target-path} \n" +
                   "  :fs cat {target-path} \n" +
                   "  :fs get {from-path} {to-path} \n" +
                   "  :fs put {from-path} {tp-path} \n" +
                   "  :fs rm {target-path} \n" +
                   "  :fs mkdir {dir-path} \n";
    return usage;
  }

  @Override
  public Object execute(List<String> args) {
    Map<String, String> mounts = getMountPoints();
    if (args.isEmpty()) {
      args.add("ls");
    }
    if (args.get(0).equalsIgnoreCase("mount")) {
      String url = args.get(1);
      String mountPoint = args.get(2);
      KnoxSession session = establishSession(mountPoint, url);
      if (session != null) {
        mounts.put(mountPoint, url);
        KnoxSession.persistMountPoints(mounts);
        return url + " mounted as " + mountPoint;
      }

      return "Failed to mount " + url + " as " + mountPoint;
    }
    else if (args.get(0).equalsIgnoreCase("unmount")) {
      String mountPoint = args.get(1);
      sessions.remove(mountPoint);
      mounts.remove(mountPoint);
      KnoxSession.persistMountPoints(mounts);
    }
    else if (args.get(0).equalsIgnoreCase("mounts")) {
      KnoxShellTable table = new KnoxShellTable();
      table.header("Mount Point").header("Topology URL");
      for (String mountPoint : mounts.keySet()) {
        table.row().value(mountPoint).value(mounts.get(mountPoint));
      }
      return table;
    }
    else if (args.get(0).equalsIgnoreCase("ls")) {
      String path = args.get(1);
      try {
        String directory;
        String mountPoint = determineMountPoint(path);
        if (mountPoint != null) {
          KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
          if (session != null) {
            directory = determineTargetPath(path, mountPoint);
            String json = Hdfs.ls(session).dir(directory).now().getString();
            Map<String,HashMap<String, ArrayList<HashMap<String, String>>>> map =
                JsonUtils.getFileStatusesAsMap(json);
            if (map != null) {
              ArrayList<HashMap<String, String>> list = map.get("FileStatuses").get("FileStatus");
              KnoxShellTable table = buildTableFromListStatus(directory, list);
              return table;
            }
          }
          else {
            return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
          }
        }
        else {
          System.out.println("No mountpoint found. Use ':fs mount {topologyURL} {mountpoint}'.");
        }
      } catch (KnoxShellException | IOException e) {
        e.printStackTrace();
      }
    }
    else if (args.get(0).equalsIgnoreCase("put")) {
      // Hdfs.put( session ).file( dataFile ).to( dataDir + "/" + dataFile ).now()
      // :fs put from-path to-path
      String localFile = args.get(1);
      String path = args.get(2);

      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
      if (session != null) {
        String targetPath = determineTargetPath(path, mountPoint);
        try {
          boolean overwrite = false;
          try {
            Response response = Hdfs.status(session).file(targetPath).now();
            if (response.exists()) {
              if (collectClearInput(targetPath + " already exists would you like to overwrite (Y/n)").equalsIgnoreCase("y")) {
                overwrite = true;
              }
            }
          } catch (KnoxShellException e) {
            // NOP
          }
          int permission = 755;
          if (args.size() >= 4) {
            permission = Integer.parseInt(args.get(3));
          }
          Hdfs.put(session).file(localFile).to(targetPath).overwrite(overwrite).permission(permission).now().getString();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      else {
        return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
      }
    }
    else if (args.get(0).equalsIgnoreCase("rm")) {
      // Hdfs.rm( session ).file( dataFile ).now()
      // :fs rm target-path
      String path = args.get(1);

      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
      if (session != null) {
        String targetPath = determineTargetPath(path, mountPoint);
        try {
          Hdfs.rm(session).file(targetPath).now().getString();
        } catch (KnoxShellException | IOException e) {
          e.printStackTrace();
        }
      }
      else {
        return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
      }
    }
    else if (args.get(0).equalsIgnoreCase("cat")) {
      // println Hdfs.get( session ).from( dataDir + "/" + dataFile ).now().string
      // :fs cat target-path
      String path = args.get(1);

      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
      if (session != null) {
        String targetPath = determineTargetPath(path, mountPoint);
        try {
          String contents = Hdfs.get(session).from(targetPath).now().getString();
          return contents;
        } catch (KnoxShellException | IOException e) {
          e.printStackTrace();
        }
      }
      else {
        return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
      }
    }
    else if (args.get(0).equalsIgnoreCase("mkdir")) {
      // println Hdfs.mkdir( session ).dir( directoryPath ).perm( "777" ).now().string
      // :fs mkdir target-path [perms]
      String path = args.get(1);
      String perms = null;
      if (args.size() == 3) {
        perms = args.get(2);
      }

      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
      if (session != null) {
        String targetPath = determineTargetPath(path, mountPoint);
        try {
          if (perms != null) {
            Hdfs.mkdir(sessions.get(mountPoint)).dir(targetPath).now().getString();
          }
          else {
            Hdfs.mkdir(session).dir(targetPath).perm(perms).now().getString();
          }
          return "Successfully created directory: " + targetPath;
        } catch (KnoxShellException | IOException e) {
          e.printStackTrace();
        }
      }
      else {
        return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
      }
    }
    else if (args.get(0).equalsIgnoreCase("get")) {
      // println Hdfs.get( session ).from( dataDir + "/" + dataFile ).now().string
      // :fs get from-path [to-path]
      String path = args.get(1);

      String mountPoint = determineMountPoint(path);
      KnoxSession session = getSessionForMountPoint(mounts, mountPoint);
      if (session != null) {
        String from = determineTargetPath(path, mountPoint);
        String to = null;
        if (args.size() > 2) {
          to = args.get(2);
        }
        else {
          to = System.getProperty("user.home") + File.separator +
              path.substring(path.lastIndexOf(File.separator));
        }
        try {
          Hdfs.get(sessions.get(mountPoint)).from(from).file(to).now().getString();
        } catch (KnoxShellException | IOException e) {
          e.printStackTrace();
        }
      }
      else {
        return "No session established for mountPoint: " + mountPoint + " Use :fs mount {topology-url} {mountpoint-name}";
      }
    }
    else {
      System.out.println("Unknown filesystem command");
      System.out.println(getUsage());
    }
    return "";
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
      e.printStackTrace();
      return null;
    }
    String username = dlg.name();
    String password = new String(dlg.chars());
    KnoxSession session = null;
    try {
      session = KnoxSession.login(url, username, password);
      sessions.put(mountPoint, session);
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return session;
  }

  private String collectClearInput(String prompt) {
    Console c = System.console();
    if (c == null) {
      System.err.println("No console.");
      System.exit(1);
    }

    String value = c.readLine(prompt);

    return value;
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
    String mountPoint = null;
    if (path.startsWith("/")) {
      // does the user supplied path starts at a root
      // if so check for a mountPoint based on the first element of the path
      String[] pathElements = path.split("/");
      mountPoint = pathElements[1];
    }
    return mountPoint;
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

    return table;
  }

  @SuppressWarnings("unchecked")
  protected Map<String, String> getMountPoints() {
    Map<String, String> mounts = (Map<String, String>) getVariables().get(KNOXMOUNTPOINTS);
    if (mounts == null) {
      try {
        mounts = KnoxSession.loadMountPoints();
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (mounts != null) {
        getVariables().put(KNOXMOUNTPOINTS, mounts);
      }
      else {
        mounts = new HashMap<>();
      }
    }
    return mounts;
  }

  public static void main(String[] args) {
    WebHDFSCommand cmd = new WebHDFSCommand(new Groovysh());
    List<String> args2 = new ArrayList<>();
    cmd.execute(args2);
  }
}
