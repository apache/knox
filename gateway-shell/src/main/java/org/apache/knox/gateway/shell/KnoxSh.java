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

import org.apache.knox.gateway.shell.jdbc.KnoxLine;
import org.apache.knox.gateway.shell.knox.token.Get;
import org.apache.knox.gateway.shell.knox.token.Token;
import org.apache.knox.gateway.shell.util.ClientTrustStoreHelper;
import org.apache.knox.gateway.util.JsonUtils;
import org.apache.knox.gateway.util.X509CertificateUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class KnoxSh {

  private static final String USAGE_PREFIX = "KnoxSh {cmd} [options]";
  private static final String COMMANDS =
      "   [--help]\n" +
      "   [" + KnoxBuildTrustStore.USAGE + "]\n" +
      "   [" + KnoxInit.USAGE + "]\n" +
      "   [" + KnoxDestroy.USAGE + "]\n" +
      "   [" + KnoxList.USAGE + "]\n" +
      "   [" + KnoxLineCommand.USAGE + "]\n";

  /** allows stdout to be captured if necessary */
  PrintStream out = System.out;
  /** allows stderr to be captured if necessary */
  PrintStream err = System.err;

  private Command command;
  private String gateway;

  public int run(String[] args) throws Exception {
    int exitCode;
    try {
      exitCode = init(args);
      if (exitCode != 0) {
        return exitCode;
      }
      if (command != null && command.validate()) {
        command.execute();
      } else {
        out.println("ERROR: Invalid Command" + "\n" + "Unrecognized option:" +
            args[0] + "\n" +
            "A fatal exception has occurred. Program will exit.");
        exitCode = -2;
      }
    } catch (Exception e) {
      e.printStackTrace( err );
      err.flush();
      return -3;
    }
    return exitCode;
  }

  /**
   * Parse the command line arguments and initialize the data
   * <pre>
   * % knoxcli version
   * % knoxcli service-test [--u user] [--p password] [--cluster clustername] [--hostname name] [--port port]
   *
   * </pre>
   * @param args command line arguments
   * @return exit code
   * @throws IOException exception on init
   */
  private int init(String[] args) throws IOException {
    if (args.length == 0) {
      printKnoxShellUsage();
      return -1;
    }
    for (int i = 0; i < args.length; i++) { // parse command line
      if ( args[i].equals("destroy") ) {
        command = new KnoxDestroy();
      } else if ( args[i].equals("buildTrustStore") ) {
        command = new KnoxBuildTrustStore();
      } else if ( args[i].equals("init") ) {
        command = new KnoxInit();
      } else if ( args[i].equals("list") ) {
        command = new KnoxList();
      } else if ( args[i].equals("knoxline") ) {
        command = new KnoxLineCommand();
      } else if (args[i].equals("--gateway")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.gateway = args[++i];
      } else if (args[i].equals("--help")) {
        printKnoxShellUsage();
        return -1;
      } else {
        printKnoxShellUsage();
        //ToolRunner.printGenericCommandUsage(System.err);
        return -1;
      }
    }
    return 0;
  }

  private void printKnoxShellUsage() {
    out.println( USAGE_PREFIX + "\n" + COMMANDS );
    if ( command != null ) {
      out.println(command.getUsage());
    }
    else {
      char[] chars = new char[79];
      Arrays.fill( chars, '=' );
      String div = new String( chars );

      out.println( div );
      out.println( KnoxInit.USAGE + "\n\n" + KnoxInit.DESC );
      out.println();
      out.println( div );
      out.println(KnoxDestroy.USAGE + "\n\n" + KnoxDestroy.DESC);
      out.println();
      out.println( div );
      out.println(KnoxList.USAGE + "\n\n" + KnoxList.DESC);
      out.println();
      out.println( div );
      out.println(KnoxLineCommand.USAGE + "\n\n" + KnoxLineCommand.DESC);
      out.println();
      out.println( div );
    }
  }

  private abstract class Command {
    public boolean validate() {
      return true;
    }

    public abstract void execute() throws Exception;

    public abstract String getUsage();
  }

  class KnoxBuildTrustStore extends Command {

    private static final String USAGE = "buildTrustStore --gateway server-url";
    private static final String DESC = "Downloads the gateway server's public certificate and builds a trust store.";
    private static final String GATEWAY_CERT_NOT_EXPORTED = "Finished work without building truststore";
    private static final String GATEWAY_CERT_EXPORTED_MESSAGE_PREFIX = "Gateway server's certificate is exported into ";

    @Override
    public void execute() throws Exception {
      String result = GATEWAY_CERT_NOT_EXPORTED;
      try {
        final X509Certificate[] gatewayServerPublicCerts = X509CertificateUtil.fetchPublicCertsFromServer(gateway, false, out);
        if (gatewayServerPublicCerts != null) {
          final File trustStoreFile = ClientTrustStoreHelper.getClientTrustStoreFile();
          final String trustStorePassword = ClientTrustStoreHelper.getClientTrustStoreFilePassword();
          X509CertificateUtil.writeCertificatesToJks(gatewayServerPublicCerts, trustStoreFile, trustStorePassword);
          result = GATEWAY_CERT_EXPORTED_MESSAGE_PREFIX + trustStoreFile.getAbsolutePath();
        }
      } catch(Exception e) {
        throw new KnoxShellException("Error while building trust store", e);
      }
      out.println(result);
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

  private class KnoxInit extends Command {

    public static final String USAGE = "init --gateway topology-url";
    public static final String DESC = "Initializes a Knox token session.";

    @Override
    public void execute() throws Exception {
      Credentials credentials = new Credentials();
      credentials.add("ClearInput", "Enter username: ", "user")
                      .add("HiddenInput", "Enter password: ", "pass");
      credentials.collect();

      String username = credentials.get("user").string();
      String pass = credentials.get("pass").string();

      KnoxSession session = null;
      Get.Response response;
      try {
        session = KnoxSession.login(gateway, username, pass);

        response = Token.get( session ).now();
        String text = response.getString();
        Map<String, String> json = JsonUtils.getMapFromJsonString(text);

        //println "Access Token: " + json.access_token
        System.out.println("knoxinit successful!");
        displayTokenDetails(json);

        File tokenfile = new File(System.getProperty("user.home"), ".knoxtokencache");
        try( OutputStream fos = Files.newOutputStream(tokenfile.toPath()) ) {
          fos.write(text.getBytes(StandardCharsets.UTF_8));
          Set<PosixFilePermission> perms = new HashSet<>();
          //add owners permission only
          perms.add(PosixFilePermission.OWNER_READ);
          perms.add(PosixFilePermission.OWNER_WRITE);
          Files.setPosixFilePermissions(Paths.get(System.getProperty("user.home") + "/.knoxtokencache"), perms);
        }
      } catch(KnoxShellException he) {
          String message = "Failure to acquire token. Please verify your credentials, Knox URL, and TLS truststore configuration.";
          Throwable t = he.getCause();
          if (t != null) {
              String rc = t.getMessage();
              if (rc != null) {
                  message += " Cause: " + rc;
              }
          }
        System.out.println(message);
      }
      if ( session != null ) {
        session.shutdown();
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class KnoxDestroy extends Command {

    public static final String USAGE = "destroy";
    public static final String DESC = "Destroys an Knox token session.";

    @Override
    public void execute() throws Exception {
      File tokenfile = new File(System.getProperty("user.home"), ".knoxtokencache");
      tokenfile.delete();
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class KnoxList extends Command {

    public static final String USAGE = "list";
    public static final String DESC = "Displays Knox token details.";

    @Override
    public void execute() throws Exception {
      String tokenFilePath = System.getProperty("user.home") +
          File.separator + ".knoxtokencache";
      if (new File(tokenFilePath).exists()) {
        String tokenfile = readFile(tokenFilePath);

        if (tokenfile != null) {
          Map<String, String> json = JsonUtils.getMapFromJsonString(tokenfile);
          displayTokenDetails(json);
        }
      }
      else {
        System.out.println("Knox token cache does not exist. Please login with init.");
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private void displayTokenDetails(Map<String, String> json) {
    System.out.println("Token Type: " + json.get("token_type"));

    DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss", Locale.getDefault());

    long milliSeconds= Long.parseLong(json.get("expires_in"));

    Calendar calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
    calendar.setTimeInMillis(milliSeconds);
    System.out.println("Expires On: " + formatter.format(calendar.getTime()));
    String targetUrl = json.get("target_url");
    if (targetUrl != null) {
      System.out.println("Target URL: " + json.get("target_url"));
    } else {
      System.out.println("No specific target URL configured.");
    }
  }

  private String readFile(String file) throws IOException {
    try (InputStream inputStream = Files.newInputStream(Paths.get(file));
         InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
         BufferedReader reader = new BufferedReader(inputStreamReader)) {
      String line;
      StringBuilder stringBuilder = new StringBuilder();
      String ls = System.getProperty("line.separator");
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
        stringBuilder.append(ls);
      }
      return stringBuilder.toString();
    }
  }

  private class KnoxLineCommand extends Command {

    public static final String USAGE = "knoxline";
    public static final String DESC = "Simple SQL Client.";

    @Override
    public void execute() throws Exception {
      KnoxLine line = new KnoxLine();
      line.execute(new ArrayList<String>().toArray(new String[0]));
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

  /**
   * @param args command line arguments
   * @throws Exception thrown if there is an issue
   */
  public static void main(String[] args) throws Exception {
    KnoxSh sh = new KnoxSh();
    int res = sh.run(args);
    System.exit(res);
  }
}
