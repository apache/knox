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
package org.apache.knox.gateway.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.thetaphi.forbiddenapis.SuppressForbidden;

public class FileUtils {

  @SuppressForbidden
  public static void chmod(String args, File file) throws IOException {
    // TODO: move to Java 7 NIO support to add windows as well
    // TODO: look into the following for Windows: Runtime.getRuntime().exec("attrib
    // -r myFile");
    if (isUnixEnv()) {
      // args and file should never be null.
      if (args == null || file == null) {
        throw new IllegalArgumentException("nullArg");
      }
      if (!file.exists()) {
        throw new IOException("fileNotFound");
      }

      // " +" regular expression for 1 or more spaces
      final String[] argsString = args.split(" +");
      List<String> cmdList = new ArrayList<>();
      cmdList.add("/bin/chmod");
      cmdList.addAll(Arrays.asList(argsString));
      cmdList.add(file.getAbsolutePath());
      new ProcessBuilder(cmdList).start();
    }
  }

  private static boolean isUnixEnv() {
    return File.separatorChar == '/';
  }

}
