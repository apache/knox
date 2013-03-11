/**
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
package org.apache.hadoop.gateway.services.security.impl;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.security.EncryptionResult;
import org.apache.hadoop.gateway.services.security.MasterService;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DefaultMasterService implements MasterService {

  private static final String MASTER_PASSPHRASE = "masterpassphrase";
  private static final String MASTER_PERSISTENCE_TAG = "#1.0# " + TimeStamp.getCurrentTime().toDateString();
  private char[] master = null;
  private AESEncryptor aes = new AESEncryptor(MASTER_PASSPHRASE);

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.services.security.impl.MasterService#getMasterSecret()
   */
  @Override
  public char[] getMasterSecret() {
    // TODO: check permission call here
    return this.master;
  }
  
  @Override
  public void init(GatewayConfig config, Map<String,String> options) throws ServiceLifecycleException {
    // for testing only
    if (options.containsKey("master")) {
      this.master = options.get("master").toCharArray();
    }
    else {
      File masterFile = new File(config.getGatewayHomeDir() + File.separator + "conf" + File.separator + "security", "master");
      if (masterFile.exists()) {
        try {
          initializeFromMaster(masterFile);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          throw new ServiceLifecycleException("Unable to load the persisted master secret.", e);
        }
      }
      else {
        if(options.get( "persist-master").equals("true")) {
          displayWarning(true);
        }
        else {
          displayWarning(false);
        }
        promptUser();
        if(options.get( "persist-master").equals("true")) {
          persistMaster(master, masterFile);
        }
      }
    }
  }
  
  private void promptUser() {
    Console c = System.console();
    if (c == null) {
        System.err.println("No console.");
        System.exit(1);
    }

    boolean noMatch;
    do {
        char [] newPassword1 = c.readPassword("Enter master secret: ");
        char [] newPassword2 = c.readPassword("Enter master secret again: ");
        noMatch = ! Arrays.equals(newPassword1, newPassword2);
        if (noMatch) {
            c.format("Passwords don't match. Try again.%n");
        } else {
            this.master = Arrays.copyOf(newPassword1, newPassword1.length);
        }
        Arrays.fill(newPassword1, ' ');
        Arrays.fill(newPassword2, ' ');
    } while (noMatch);
  }

  private void displayWarning(boolean persisting) {
    Console c = System.console();
    if (c == null) {
        System.err.println("No console.");
        System.exit(1);
    }
    if (persisting) {
      c.printf("***************************************************************************************************\n");
      c.printf("You have indicated that you would like to persist the master secret for this gateway instance.\n");
      c.printf("Be aware that this is less secure than manually entering the secret on startup.\n");
      c.printf("The persisted file will be encrypted and primarily protected through OS permissions.\n");
      c.printf("***************************************************************************************************\n");
    }
    else {
      c.printf("***************************************************************************************************\n");
      c.printf("Be aware that you will need to enter your master secret for future starts exactly as you do here.\n");
      c.printf("This secret is needed to access protected resources for the gateway process.\n");
      c.printf("The master secret must be protected, kept secret and not stored in clear text anywhere.\n");
      c.printf("***************************************************************************************************\n");
    }
  }

  private void persistMaster(char[] master, File masterFile) {
    EncryptionResult atom = encryptMaster(master);
    try {
      ArrayList<String> lines = new ArrayList<String>();
      lines.add(MASTER_PERSISTENCE_TAG);
      
      String line = Base64.encodeBase64String((
          Base64.encodeBase64String(atom.salt) + "::" + 
          Base64.encodeBase64String(atom.iv) + "::" + 
          Base64.encodeBase64String(atom.cipher)).getBytes("UTF8"));
      lines.add(line);
      FileUtils.writeLines(masterFile, "UTF8", lines);
      
      // restrict os permissions to only the user running this process
      chmod("600", masterFile);
    } catch (IOException e) {
      // TODO log appropriate message that the master secret has not been persisted
      e.printStackTrace();
    }
  }

  private EncryptionResult encryptMaster(char[] master) {
    // TODO Auto-generated method stub
    try {
      return aes.encrypt(new String(master));
    } catch (Exception e) {
      // TODO log failed encryption attempt
      // need to ensure that we don't persist now
      e.printStackTrace();
    }
    return null;
  }

  private void initializeFromMaster(File masterFile) throws Exception {
    try {
      List<String> lines = FileUtils.readLines(masterFile, "UTF8");
      String tag = lines.get(0);
      // TODO: log - if appropriate - at least at finest level
      System.out.println("Loading from persistent master: " + tag);
      String line = new String(Base64.decodeBase64(lines.get(1)));
      String[] parts = line.split("::");
//System.out.println("salt: " + parts[0] + " : " + Base64.decodeBase64(parts[0]));
//System.out.println("iv: " + parts[1]);
//System.out.println("cipher: " + parts[2]);
      this.master = new String(aes.decrypt(Base64.decodeBase64(parts[0]), Base64.decodeBase64(parts[1]), Base64.decodeBase64(parts[2])), "UTF8").toCharArray();
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      throw e;
    }
  }

  @Override
  public void start() throws ServiceLifecycleException {
  }

  @Override
  public void stop() throws ServiceLifecycleException {
  }
  
  private void chmod(String args, File file) throws IOException
  {
      // TODO: move to Java 7 NIO support to add windows as well
      // TODO: look into the following for Windows: Runtime.getRuntime().exec("attrib -r myFile");
      if (isUnixEnv()) {
          //args and file should never be null.
          if (args == null || file == null) 
            throw new IOException("nullArg");
          if (!file.exists()) 
            throw new IOException("fileNotFound");

          // " +" regular expression for 1 or more spaces
          final String[] argsString = args.split(" +");
          List<String> cmdList = new ArrayList<String>();
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
