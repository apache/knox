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
package org.apache.knox.gateway.services.security.impl;

import de.thetaphi.forbiddenapis.SuppressForbidden;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.EncryptionResult;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CMFMasterService {
  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );

  private static final String MASTER_PASSPHRASE = "masterpassphrase";
  private static final String MASTER_PERSISTENCE_TAG = "#1.0# " + TimeStamp.getCurrentTime().toDateString();
  protected char[] master;
  protected String serviceName;
  private ConfigurableEncryptor encryptor = new ConfigurableEncryptor(MASTER_PASSPHRASE);

  public CMFMasterService(String serviceName) {
    super();
    this.serviceName = serviceName;
  }

  public char[] getMasterSecret() {
    return this.master;
  }

  public void setupMasterSecret(String securityDir, String filename,
      boolean persisting, GatewayConfig config)
          throws ServiceLifecycleException {
      encryptor.init(config);
      setupMasterSecret(securityDir, filename, persisting);
  }

  protected void setupMasterSecret(String securityDir, boolean persisting) throws ServiceLifecycleException {
    setupMasterSecret(securityDir, serviceName + "-master", persisting);
  }

  protected void setupMasterSecret(String securityDir, String filename, boolean persisting) throws ServiceLifecycleException {
    File masterFile = new File(securityDir, filename);
    if (masterFile.exists()) {
      try {
        initializeFromMaster(masterFile);
      } catch (Exception e) {
        throw new ServiceLifecycleException("Unable to load the persisted master secret.", e);
      }
    }
    else {
    if (master == null) {
        displayWarning(persisting);
        promptUser();
      }
      if(persisting) {
        persistMaster(master, masterFile);
      }
    }
  }

  protected void promptUser() {
    Console c = System.console();
    if (c == null) {
      LOG.unableToPromptForMasterUseKnoxCLI();
      System.err.println("No console.");
      System.exit(1);
    }

    boolean valid = false;
    do {
        char [] newPassword1 = c.readPassword("Enter master secret: ");
        char [] newPassword2 = c.readPassword("Enter master secret again: ");
        if ( newPassword1.length == 0 ) {
            c.format("Password too short. Try again.%n");
        } else if (!Arrays.equals(newPassword1, newPassword2) ) {
            c.format("Passwords don't match. Try again.%n");
        } else {
            this.master = Arrays.copyOf(newPassword1, newPassword1.length);
            valid = true;
        }
        Arrays.fill(newPassword1, ' ');
        Arrays.fill(newPassword2, ' ');
    } while (!valid);
  }

  protected void displayWarning(boolean persisting) {
    Console c = System.console();
    if (c == null) {
        LOG.unableToPromptForMasterUseKnoxCLI();
        System.err.println("No console.");
        System.exit(1);
    }
    if (persisting) {
      c.printf("***************************************************************************************************\n");
      c.printf("You have indicated that you would like to persist the master secret for this service instance.\n");
      c.printf("Be aware that this is less secure than manually entering the secret on startup.\n");
      c.printf("The persisted file will be encrypted and primarily protected through OS permissions.\n");
      c.printf("***************************************************************************************************\n");
    }
    else {
      c.printf("***************************************************************************************************\n");
      c.printf("Be aware that you will need to enter your master secret for future starts exactly as you do here.\n");
      c.printf("This secret is needed to access protected resources for the service process.\n");
      c.printf("The master secret must be protected, kept secret and not stored in clear text anywhere.\n");
      c.printf("***************************************************************************************************\n");
    }
  }

  protected void persistMaster(char[] master, File masterFile) {
    try {
      EncryptionResult atom = encryptMaster(master);
      ArrayList<String> lines = new ArrayList<>();
      lines.add(MASTER_PERSISTENCE_TAG);

      String line = Base64.encodeBase64String((
          Base64.encodeBase64String(atom.salt) + "::" +
          Base64.encodeBase64String(atom.iv) + "::" +
          Base64.encodeBase64String(atom.cipher)).getBytes(StandardCharsets.UTF_8));
      lines.add(line);
      FileUtils.writeLines(masterFile, StandardCharsets.UTF_8.name(), lines);

      // restrict os permissions to only the user running this process
      chmod("600", masterFile);
    } catch (IOException e) {
      LOG.failedToPersistMasterSecret(e);
    }
  }

  private EncryptionResult encryptMaster(char[] master) throws IOException {
    try {
      return encryptor.encrypt(new String(master));
    } catch (Exception e) {
      LOG.failedToEncryptMasterSecret(e);
      throw new IOException(e);
    }
  }

  protected void initializeFromMaster(File masterFile) throws Exception {
      try {
        List<String> lines = FileUtils.readLines(masterFile, StandardCharsets.UTF_8);
        String tag = lines.get(0);
        LOG.loadingFromPersistentMaster( tag );
        String line = new String(Base64.decodeBase64(lines.get(1)), StandardCharsets.UTF_8);
        String[] parts = line.split("::");
        this.master = new String(encryptor.decrypt(Base64.decodeBase64(parts[0]),
            Base64.decodeBase64(parts[1]), Base64.decodeBase64(parts[2])),
            StandardCharsets.UTF_8).toCharArray();
      } catch (Exception e) {
        LOG.failedToInitializeFromPersistentMaster(masterFile.getName(), e);
        throw e;
      }
  }

  @SuppressForbidden
  private void chmod(String args, File file) throws IOException {
      // TODO: move to Java 7 NIO support to add windows as well
      // TODO: look into the following for Windows: Runtime.getRuntime().exec("attrib -r myFile");
      if (isUnixEnv()) {
          //args and file should never be null.
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

  private boolean isUnixEnv() {
    return (File.separatorChar == '/');
  }
}
