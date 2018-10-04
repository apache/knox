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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.knox.gateway.GatewayCommandLine;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.deploy.DeploymentFactory;
import org.apache.knox.gateway.services.CLIGatewayServices;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.impl.X509CertificateUtil;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.validation.TopologyValidator;
import org.apache.log4j.PropertyConfigurator;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.Factory;
import org.apache.shiro.util.ThreadContext;
import org.eclipse.persistence.oxm.MediaType;
import org.jboss.shrinkwrap.api.exporter.ExplodedExporter;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 *
 */
public class KnoxCLI extends Configured implements Tool {

  private static final String USAGE_PREFIX = "KnoxCLI {cmd} [options]";
  static final private String COMMANDS =
      "   [--help]\n" +
      "   [" + VersionCommand.USAGE + "]\n" +
      "   [" + MasterCreateCommand.USAGE + "]\n" +
      "   [" + CertCreateCommand.USAGE + "]\n" +
      "   [" + CertExportCommand.USAGE + "]\n" +
      "   [" + AliasCreateCommand.USAGE + "]\n" +
      "   [" + AliasDeleteCommand.USAGE + "]\n" +
      "   [" + AliasListCommand.USAGE + "]\n" +
      "   [" + RedeployCommand.USAGE + "]\n" +
      "   [" + ListTopologiesCommand.USAGE + "]\n" +
      "   [" + ValidateTopologyCommand.USAGE + "]\n" +
      "   [" + LDAPAuthCommand.USAGE + "]\n" +
      "   [" + LDAPSysBindCommand.USAGE + "]\n" +
      "   [" + ServiceTestCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryClientsListCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryListProviderConfigsCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryUploadProviderConfigCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryListDescriptorsCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryUploadDescriptorCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryDeleteProviderConfigCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryDeleteDescriptorCommand.USAGE + "]\n" +
      "   [" + RemoteRegistryGetACLCommand.USAGE + "]\n";

  /** allows stdout to be captured if necessary */
  public PrintStream out = System.out;
  /** allows stderr to be captured if necessary */
  public PrintStream err = System.err;

  private static GatewayServices services = new CLIGatewayServices();
  private Command command;
  private String value = null;
  private String cluster = null;
  private String path = null;
  private String generate = "false";
  private String hostname = null;
  private String port = null;
  private boolean force = false;
  private boolean debug = false;
  private String user = null;
  private String pass = null;
  private boolean groups = false;

  private String remoteRegistryClient = null;
  private String remoteRegistryEntryName = null;

  // For testing only
  private String master = null;
  private String type = null;

  /* (non-Javadoc)
   * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
   */
  @Override
  public int run(String[] args) throws Exception {
    int exitCode = 0;
    try {
      exitCode = init(args);
      if (exitCode != 0) {
        return exitCode;
      }
      if (command != null && command.validate()) {
        initializeServices( command instanceof MasterCreateCommand );
        command.execute();
      } else if (!(command instanceof MasterCreateCommand)){
        out.println("ERROR: Invalid Command" + "\n" + "Unrecognized option:" +
            args[0] + "\n" +
            "A fatal exception has occurred. Program will exit.");
        exitCode = -2;
      }
    } catch (ServiceLifecycleException sle) {
      out.println("ERROR: Internal Error: Please refer to the knoxcli.log " +
          "file for details. " + sle.getMessage());
    } catch (Exception e) {
      e.printStackTrace( err );
      err.flush();
      return -3;
    }
    return exitCode;
  }

  GatewayServices getGatewayServices() {
    return services;
  }

  private void initializeServices(boolean persisting) throws ServiceLifecycleException {
    GatewayConfig config = getGatewayConfig();
    Map<String,String> options = new HashMap<>();
    options.put(GatewayCommandLine.PERSIST_LONG, Boolean.toString(persisting));
    if (master != null) {
      options.put("master", master);
    }
    services.init(config, options);
  }

  /**
   * Parse the command line arguments and initialize the data
   * <pre>
   * % knoxcli version
   * % knoxcli list-topologies
   * % knoxcli master-create keyName [--size size] [--generate]
   * % knoxcli create-alias alias [--cluster clustername] [--generate] [--value v]
   * % knoxcli list-alias [--cluster clustername]
   * % knoxcli delete=alias alias [--cluster clustername]
   * % knoxcli create-cert alias [--hostname h]
   * % knoxcli redeploy [--cluster clustername]
   * % knoxcli validate-topology [--cluster clustername] | [--path <path/to/file>]
   * % knoxcli user-auth-test [--cluster clustername] [--u username] [--p password]
   * % knoxcli system-user-auth-test [--cluster clustername] [--d]
   * % knoxcli service-test [--u user] [--p password] [--cluster clustername] [--hostname name] [--port port]
   * % knoxcli list-registry-clients
   * % knoxcli get-registry-acl entryName --registry-client name
   * % knoxcli list-provider-configs --registry-client
   * % knoxcli upload-provider-config filePath --registry-client name [--entry-name entryName]
   * % knoxcli list-descriptors --registry-client
   * % knoxcli upload-descriptor filePath --registry-client name [--entry-name entryName]
   * % knoxcli delete-provider-config providerConfig --registry-client name
   * % knoxcli delete-descriptor descriptor --registry-client name
   * </pre>
   * @param args
   * @return
   * @throws IOException
   */
  private int init(String[] args) throws IOException {
    if (args.length == 0) {
      printKnoxShellUsage();
      return -1;
    }
    for (int i = 0; i < args.length; i++) { // parse command line
      if (args[i].equals("create-master")) {
        command = new MasterCreateCommand();
        if ((args.length > i + 1) && args[i + 1].equals("--help")) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("delete-alias")) {
        String alias = null;
        if (args.length >= 2) {
          alias = args[++i];
        }
        command = new AliasDeleteCommand(alias);
        if (alias == null || alias.equals("--help")) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("create-alias")) {
        String alias = null;
        if (args.length >= 2) {
          alias = args[++i];
        }
        command = new AliasCreateCommand(alias);
        if (alias == null || alias.equals("--help")) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("create-cert")) {
        command = new CertCreateCommand();
        if ((args.length > i + 1) && args[i + 1].equals("--help")) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("export-cert")) {
        command = new CertExportCommand();
        if ((args.length > i + 1) && args[i + 1].equals("--help")) {
          printKnoxShellUsage();
          return -1;
        }
      }else if(args[i].equals("user-auth-test")) {
        if(i + 1 >= args.length) {
          printKnoxShellUsage();
          return -1;
        } else {
          command = new LDAPAuthCommand();
        }
      } else if(args[i].equals("system-user-auth-test")) {
        if (i + 1 >= args.length){
          printKnoxShellUsage();
          return -1;
        } else {
          command = new LDAPSysBindCommand();
        }
      } else if (args[i].equals("list-alias")) {
        command = new AliasListCommand();
      } else if (args[i].equals("--value")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.value = args[++i];
        if ( command != null && command instanceof MasterCreateCommand ) {
          this.master = this.value;
        }
      } else if ( args[i].equals("version") ) {
        command = new VersionCommand();
      } else if ( args[i].equals("redeploy") ) {
        command = new RedeployCommand();
      } else if ( args[i].equals("validate-topology") ) {
        if(i + 1 >= args.length) {
          printKnoxShellUsage();
          return -1;
        } else {
          command = new ValidateTopologyCommand();
        }
      } else if( args[i].equals("list-topologies") ){
        command = new ListTopologiesCommand();
      }else if ( args[i].equals("--cluster") || args[i].equals("--topology") ) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.cluster = args[++i];
      } else if (args[i].equals("service-test")) {
        if( i + 1 >= args.length) {
          printKnoxShellUsage();
          return -1;
        } else {
          command = new ServiceTestCommand();
        }
      } else if (args[i].equals("--generate")) {
        if ( command != null && command instanceof MasterCreateCommand ) {
          this.master = UUID.randomUUID().toString();
        } else {
          this.generate = "true";
        }
      } else if(args[i].equals("--type")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.type = args[++i];
      } else if(args[i].equals("--path")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.path = args[++i];
      }else if (args[i].equals("--hostname")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.hostname = args[++i];
      } else if (args[i].equals("--port")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.port = args[++i];
      } else if (args[i].equals("--master")) {
        // For testing only
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.master = args[++i];
      } else if (args[i].equals("--force")) {
        this.force = true;
      } else if (args[i].equals("--help")) {
        printKnoxShellUsage();
        return -1;
      } else if(args[i].equals("--d")) {
        this.debug = true;
      } else if(args[i].equals("--u")) {
        if(i + 1 <= args.length) {
          this.user = args[++i];
        } else{
          printKnoxShellUsage();
          return -1;
        }
      } else if(args[i].equals("--p")) {
        if(i + 1 <= args.length) {
          this.pass = args[++i];
        } else{
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("--g")) {
        this.groups = true;
      } else if (args[i].equals("list-registry-clients")) {
        command = new RemoteRegistryClientsListCommand();
      } else if (args[i].equals("--registry-client")) {
        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
          printKnoxShellUsage();
          return -1;
        }
        this.remoteRegistryClient = args[++i];
      } else if (args[i].equalsIgnoreCase("list-provider-configs")) {
        command = new RemoteRegistryListProviderConfigsCommand();
      } else if (args[i].equalsIgnoreCase("list-descriptors")) {
        command = new RemoteRegistryListDescriptorsCommand();
      } else if (args[i].equalsIgnoreCase("upload-provider-config")) {
        String fileName;
        if (i <= (args.length - 1)) {
          fileName = args[++i];
          command = new RemoteRegistryUploadProviderConfigCommand(fileName);
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("upload-descriptor")) {
        String fileName;
        if (i <= (args.length - 1)) {
          fileName = args[++i];
          command = new RemoteRegistryUploadDescriptorCommand(fileName);
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("--entry-name")) {
        if (i <= (args.length - 1)) {
          remoteRegistryEntryName = args[++i];
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("delete-descriptor")) {
        if (i <= (args.length - 1)) {
          String entry = args[++i];
          command = new RemoteRegistryDeleteDescriptorCommand(entry);
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("delete-provider-config")) {
        if (i <= (args.length - 1)) {
          String entry = args[++i];
          command = new RemoteRegistryDeleteProviderConfigCommand(entry);
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equalsIgnoreCase("get-registry-acl")) {
        if (i <= (args.length - 1)) {
          String entry = args[++i];
          command = new RemoteRegistryGetACLCommand(entry);
        } else {
          printKnoxShellUsage();
          return -1;
        }
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
    } else {
      char[] chars = new char[79];
      Arrays.fill( chars, '=' );
      String div = new String( chars );

      out.println( div );
      out.println( VersionCommand.USAGE + "\n\n" + VersionCommand.DESC );
      out.println();
      out.println( div );
      out.println( MasterCreateCommand.USAGE + "\n\n" + MasterCreateCommand.DESC );
      out.println();
      out.println( div );
      out.println( CertCreateCommand.USAGE + "\n\n" + CertCreateCommand.DESC );
      out.println();
      out.println( div );
      out.println( CertExportCommand.USAGE + "\n\n" + CertExportCommand.DESC );
      out.println();
      out.println( div );
      out.println( AliasCreateCommand.USAGE + "\n\n" + AliasCreateCommand.DESC );
      out.println();
      out.println( div );
      out.println( AliasDeleteCommand.USAGE + "\n\n" + AliasDeleteCommand.DESC );
      out.println();
      out.println( div );
      out.println( AliasListCommand.USAGE + "\n\n" + AliasListCommand.DESC );
      out.println();
      out.println( div );
      out.println( RedeployCommand.USAGE + "\n\n" + RedeployCommand.DESC );
      out.println();
      out.println( div );
      out.println(ValidateTopologyCommand.USAGE + "\n\n" + ValidateTopologyCommand.DESC);
      out.println();
      out.println( div );
      out.println(ListTopologiesCommand.USAGE + "\n\n" + ListTopologiesCommand.DESC);
      out.println();
      out.println( div );
      out.println(LDAPAuthCommand.USAGE + "\n\n" + LDAPAuthCommand.DESC);
      out.println();
      out.println( div );
      out.println(LDAPSysBindCommand.USAGE + "\n\n" + LDAPSysBindCommand.DESC);
      out.println();
      out.println( div );
      out.println(ServiceTestCommand.USAGE + "\n\n" + ServiceTestCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryClientsListCommand.USAGE + "\n\n" + RemoteRegistryClientsListCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryGetACLCommand.USAGE + "\n\n" + RemoteRegistryGetACLCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryListProviderConfigsCommand.USAGE + "\n\n" + RemoteRegistryListProviderConfigsCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryListDescriptorsCommand.USAGE + "\n\n" + RemoteRegistryListDescriptorsCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryUploadProviderConfigCommand.USAGE + "\n\n" + RemoteRegistryUploadProviderConfigCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryUploadDescriptorCommand.USAGE + "\n\n" + RemoteRegistryUploadDescriptorCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryDeleteProviderConfigCommand.USAGE + "\n\n" + RemoteRegistryDeleteProviderConfigCommand.DESC);
      out.println();
      out.println( div );
      out.println(RemoteRegistryDeleteDescriptorCommand.USAGE + "\n\n" + RemoteRegistryDeleteDescriptorCommand.DESC);
      out.println();
      out.println( div );
    }
  }

  private abstract class Command {

    public boolean validate() {
      return true;
    }

    protected Service getService(String serviceName) {
      Service service = null;

      return service;
    }

    public abstract void execute() throws Exception;

    public abstract String getUsage();

    protected AliasService getAliasService() {
      AliasService as = services.getService(GatewayServices.ALIAS_SERVICE);
      return as;
    }

    protected KeystoreService getKeystoreService() {
      KeystoreService ks = services.getService(GatewayServices.KEYSTORE_SERVICE);
      return ks;
    }

    protected TopologyService getTopologyService()  {
      TopologyService ts = services.getService(GatewayServices.TOPOLOGY_SERVICE);
      return ts;
    }

    protected RemoteConfigurationRegistryClientService getRemoteConfigRegistryClientService() {
      return services.getService(GatewayServices.REMOTE_REGISTRY_CLIENT_SERVICE);
    }

  }

 private class AliasListCommand extends Command {

  public static final String USAGE = "list-alias [--cluster clustername]";
  public static final String DESC = "The list-alias command lists all of the aliases\n" +
                                    "for the given hadoop --cluster. The default\n" +
                                    "--cluster being the gateway itself.";

   /* (non-Javadoc)
    * @see KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     AliasService as = getAliasService();
      KeystoreService keystoreService = getKeystoreService();

     if (cluster == null) {
       cluster = "__gateway";
     }
      boolean credentialStoreForClusterAvailable =
          keystoreService.isCredentialStoreForClusterAvailable(cluster);
      if (credentialStoreForClusterAvailable) {
        out.println("Listing aliases for: " + cluster);
        List<String> aliases = as.getAliasesForCluster(cluster);
        for (String alias : aliases) {
          out.println(alias);
        }
        out.println("\n" + aliases.size() + " items.");
      } else {
        out.println("Invalid cluster name provided: " + cluster);
      }
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }
 }

 public class CertExportCommand extends Command {

   public static final String USAGE = "export-cert";
   public static final String DESC = "The export-cert command exports the public certificate\n" +
                                     "from the a gateway.jks keystore with the alias of gateway-identity.";
   private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
   private static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";

    public CertExportCommand() {
    }

    private GatewayConfig getGatewayConfig() {
      GatewayConfig result;
      Configuration conf = getConf();
      if( conf != null && conf instanceof GatewayConfig ) {
        result = (GatewayConfig)conf;
      } else {
        result = new GatewayConfigImpl();
      }
      return result;
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
     */
    @Override
    public void execute() throws Exception {
      KeystoreService ks = getKeystoreService();

      AliasService as = getAliasService();

      if (ks != null) {
        try {
          if (!ks.isKeystoreForGatewayAvailable()) {
            out.println("No keystore has been created for the gateway. Please use the create-cert command or populate with a CA signed cert of your own.");
          }
          char[] passphrase = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
          if (passphrase == null) {
            MasterService ms = services.getService("MasterService");
            passphrase = ms.getMasterSecret();
          }
          Certificate cert = ks.getKeystoreForGateway().getCertificate("gateway-identity");
          String keyStoreDir = getGatewayConfig().getGatewaySecurityDir() + File.separator + "keystores" + File.separator;
          File ksd = new File(keyStoreDir);
          if (!ksd.exists()) {
            if( !ksd.mkdirs() ) {
              // certainly should not happen if the keystore is known to be available
              throw new ServiceLifecycleException("Unable to create keystores directory" + ksd.getAbsolutePath());
            }
          }
          if ("PEM".equals(type) || type == null) {
            X509CertificateUtil.writeCertificateToFile(cert, new File(keyStoreDir + "gateway-identity.pem"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-identity.pem");
          }
          else if ("JKS".equals(type)) {
            X509CertificateUtil.writeCertificateToJKS(cert, new File(keyStoreDir + "gateway-client-trust.jks"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-client-trust.jks");
          }
          else {
            out.println("Invalid type for export file provided. Export has not been done. Please use: [PEM|JKS] default value is PEM.");
          }
        } catch (KeystoreServiceException e) {
          throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
        }
      }
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
     */
    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

 public class CertCreateCommand extends Command {

  public static final String USAGE = "create-cert [--hostname h]";
  public static final String DESC = "The create-cert command creates and populates\n" +
                                    "a gateway.jks keystore with a self-signed certificate\n" +
                                    "to be used as the gateway identity. It also adds an alias\n" +
                                    "to the __gateway-credentials.jceks credential store for the\n" +
                                    "key passphrase.";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";
  private static final String GATEWAY_IDENTITY_PASSPHRASE = "gateway-identity-passphrase";

   public CertCreateCommand() {
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     KeystoreService ks = getKeystoreService();

     AliasService as = getAliasService();

     if (ks != null) {
       try {
         if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
//           log.creatingCredentialStoreForGateway();
           ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
         }
         else {
//           log.credentialStoreForGatewayFoundNotCreating();
         }
         // LET'S NOT GENERATE A DIFFERENT KEY PASSPHRASE BY DEFAULT ANYMORE
         // IF A DEPLOYMENT WANTS TO CHANGE THE KEY PASSPHRASE TO MAKE IT MORE SECURE THEN
         // THEY CAN ADD THE ALIAS EXPLICITLY WITH THE CLI
         //as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
       } catch (KeystoreServiceException e) {
         throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
       }

       try {
         if (!ks.isKeystoreForGatewayAvailable()) {
//           log.creatingKeyStoreForGateway();
           ks.createKeystoreForGateway();
         }
         else {
//           log.keyStoreForGatewayFoundNotCreating();
         }
         char[] passphrase = as.getPasswordFromAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
         if (passphrase == null) {
           MasterService ms = services.getService("MasterService");
           passphrase = ms.getMasterSecret();
         }
         ks.addSelfSignedCertForGateway("gateway-identity", passphrase, hostname);
//         logAndValidateCertificate();
         out.println("Certificate gateway-identity has been successfully created.");
       } catch (KeystoreServiceException e) {
         throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
       }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }

 }

 public class AliasCreateCommand extends Command {

  public static final String USAGE = "create-alias aliasname [--cluster clustername] " +
                                     "[ (--value v) | (--generate) ]";
  public static final String DESC = "The create-alias command will create an alias\n"
                                       + "and secret pair within the credential store for the\n"
                                       + "indicated --cluster otherwise within the gateway\n"
                                       + "credential store. The actual secret may be specified via\n"
                                       + "the --value option or --generate (will create a random secret\n"
                                       + "for you) or user will be prompt to provide password.";

  private String name = null;

  /**
    * @param alias
    */
   public AliasCreateCommand(String alias) {
     name = alias;
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     AliasService as = getAliasService();
     if (cluster == null) {
       cluster = "__gateway";
     }
     if (value != null) {
       as.addAliasForCluster(cluster, name, value);
       out.println(name + " has been successfully created.");
     }
     else {
       if ("true".equals(generate)) {
         as.generateAliasForCluster(cluster, name);
         out.println(name + " has been successfully generated.");
       }
       else {
          value = new String(promptUserForPassword());
          as.addAliasForCluster(cluster, name, value);
          out.println(name + " has been successfully created.");
       }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }

    protected char[] promptUserForPassword() {
      char[] password = null;
      Console c = System.console();
      if (c == null) {
        System.err
            .println("No console to fetch password from user.Consider setting via --generate or --value.");
        System.exit(1);
      }

      boolean noMatch;
      do {
        char[] newPassword1 = c.readPassword("Enter password: ");
        char[] newPassword2 = c.readPassword("Enter password again: ");
        noMatch = !Arrays.equals(newPassword1, newPassword2);
        if (noMatch) {
          c.format("Passwords don't match. Try again.%n");
        } else {
          password = Arrays.copyOf(newPassword1, newPassword1.length);
        }
        Arrays.fill(newPassword1, ' ');
        Arrays.fill(newPassword2, ' ');
      } while (noMatch);
      return password;
    }

 }

 /**
  *
  */
 public class AliasDeleteCommand extends Command {
  public static final String USAGE = "delete-alias aliasname [--cluster clustername]";
  public static final String DESC = "The delete-alias command removes the\n" +
                                    "indicated alias from the --cluster specific\n" +
                                    "credential store or the gateway credential store.";

  private String name = null;

  /**
    * @param alias
    */
   public AliasDeleteCommand(String alias) {
     name = alias;
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     AliasService as = getAliasService();
      KeystoreService keystoreService = getKeystoreService();
     if (as != null) {
       if (cluster == null) {
         cluster = "__gateway";
       }
        boolean credentialStoreForClusterAvailable =
            keystoreService.isCredentialStoreForClusterAvailable(cluster);
        if (credentialStoreForClusterAvailable) {
          List<String> aliasesForCluster = as.getAliasesForCluster(cluster);
          if (null == aliasesForCluster || !aliasesForCluster.contains(name)) {
            out.println("Deletion of Alias: " + name + " from cluster: " + cluster + " Failed. "
                + "\n" + "No such alias exists in the cluster.");
          } else {
            as.removeAliasForCluster(cluster, name);
            out.println(name + " has been successfully deleted.");
          }
        } else {
          out.println("Invalid cluster name provided: " + cluster);
        }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }

 }

 /**
  *
  */
 public class MasterCreateCommand extends Command {
  public static final String USAGE = "create-master [--force]";
  public static final String DESC = "The create-master command persists the\n" +
                                    "master secret in a file located at:\n" +
                                    "{GATEWAY_HOME}/data/security/master. It\n" +
                                    "will prompt the user for the secret to persist.\n" +
                                    "Use --force to overwrite the master secret.";

   public MasterCreateCommand() {
   }

   private GatewayConfig getGatewayConfig() {
     GatewayConfig result;
     Configuration conf = getConf();
     if( conf != null && conf instanceof GatewayConfig ) {
       result = (GatewayConfig)conf;
     } else {
       result = new GatewayConfigImpl();
     }
     return result;
   }

   public boolean validate() {
     boolean valid = true;
     GatewayConfig config = getGatewayConfig();
     File dir = new File( config.getGatewaySecurityDir() );
     File file = new File( dir, "master" );
     if( file.exists() ) {
       if( force ) {
         if( !file.canWrite() ) {
           out.println(
               "This command requires write permissions on the master secret file: " +
                   file.getAbsolutePath() );
           valid = false;
         } else if( !file.canWrite() ) {
           out.println(
               "This command requires write permissions on the master secret file: " +
                   file.getAbsolutePath() );
           valid = false;
         } else {
           valid = file.delete();
           if( !valid ) {
             out.println(
                 "Unable to delete the master secret file: " +
                     file.getAbsolutePath() );
           }
         }
       } else {
         out.println(
             "Master secret is already present on disk. " +
                 "Please be aware that overwriting it will require updating other security artifacts. " +
                 " Use --force to overwrite the existing master secret." );
         valid = false;
       }
     } else if( dir.exists() && !dir.canWrite() ) {
       out.println(
           "This command requires write permissions on the security directory: " +
               dir.getAbsolutePath() );
       valid = false;
     }
     return valid;
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     out.println("Master secret has been persisted to disk.");
   }

   /* (non-Javadoc)
    * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }
 }

  private class VersionCommand extends Command {

    public static final String USAGE = "version";
    public static final String DESC = "Displays Knox version information.";

    @Override
    public void execute() throws Exception {
      Properties buildProperties = loadBuildProperties();
      System.out.println(
          String.format(Locale.ROOT,
              "Apache Knox: %s (%s)",
              buildProperties.getProperty( "build.version", "unknown" ),
              buildProperties.getProperty( "build.hash", "unknown" ) ) );
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class RedeployCommand extends Command {

    public static final String USAGE = "redeploy [--cluster clustername]";
    public static final String DESC =
        "Redeploys one or all of the gateway's clusters (a.k.a topologies).";

    @Override
    public void execute() throws Exception {
      TopologyService ts = getTopologyService();
      ts.reloadTopologies();
      if (cluster != null) {
        if (validateClusterName(cluster, ts)) {
          ts.redeployTopologies(cluster);
        }
        else {
          out.println("Invalid cluster name provided. Nothing to redeploy.");
        }
      }
    }

    /**
     * @param cluster
     * @param ts
     */
    private boolean validateClusterName(String cluster, TopologyService ts) {
      boolean valid = false;
      for (Topology t : ts.getTopologies() ) {
        if (t.getName().equals(cluster)) {
          valid = true;
          break;
        }
      }
      return valid;
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  private class ValidateTopologyCommand extends Command {

    public static final String USAGE = "validate-topology [--cluster clustername] | [--path \"path/to/file\"]";
    public static final String DESC = "Ensures that a cluster's description (a.k.a topology) \n" +
        "follows the correct formatting rules.\n" +
        "use the list-topologies command to get a list of available cluster names";
    private String file = "";

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    public void execute() throws Exception {
      GatewayConfig gc = getGatewayConfig();
      String topDir = gc.getGatewayTopologyDir();

      if(path != null) {
        file = path;
      } else if(cluster == null) {
        // The following block of code retreieves the list of files in the topologies directory
        File tops = new File(topDir + "/topologies");
        if(tops.isDirectory()) {
          out.println("List of files available in the topologies directory");
          for (File f : tops.listFiles()) {
            if(f.getName().endsWith(".xml")) {
              String fName = f.getName().replace(".xml", "");
              out.println(fName);
            }
          }
          return;
        } else {
          out.println("Could not locate topologies directory");
          return;
        }

      } else {
        file = topDir + "/" + cluster + ".xml";
      }

      // The following block checks a topology against the XSD
      out.println();
      out.println("File to be validated: ");
      out.println(file);
      out.println("==========================================");

      if(new File(file).exists()) {
        TopologyValidator tv = new TopologyValidator(file);

        if(tv.validateTopology()) {
          out.println("Topology file validated successfully");
        } else {
          out.println(tv.getErrorString()) ;
          out.println("Topology validation unsuccessful");
        }
      } else {
        out.println("The topology file specified does not exist.");
      }
    }

  }

  private class ListTopologiesCommand extends Command {

    public static final String USAGE = "list-topologies";
    public static final String DESC = "Retrieves a list of the available topologies within the\n" +
        "default topologies directory. Will return topologies that may not be deployed due\n" +
        "errors in file formatting.";

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    @Override
    public void execute() {

      String confDir = getGatewayConfig().getGatewayConfDir();
      File tops = new File(confDir + "/topologies");
      out.println("List of files available in the topologies directory");
      out.println(tops.toString());
      if(tops.isDirectory()) {
        for (File f : tops.listFiles()) {
          if(f.getName().endsWith(".xml")) {
            String fName = f.getName().replace(".xml", "");
            out.println(fName);
          }
        }
        return;
      } else {
        out.println("ERR: Topologies directory does not exist.");
        return;
      }

    }

  }

  private class LDAPCommand extends Command {

    public static final String USAGE = "ldap-command";
    public static final String DESC = "This is an internal command. It should not be used.";
    protected String username = null;
    protected char[] password = null;
    protected static final String debugMessage = "For more information use --d for debug output.";
    protected Topology topology;

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    @Override
    public void execute() {
      out.println("This command does not have any functionality.");
    }


//    First define a few Exceptions
    protected class NoSuchTopologyException extends Exception {
      public NoSuchTopologyException() {}
      public NoSuchTopologyException(String message) { super(message); }
    }
    protected class MissingPasswordException extends Exception {
      public MissingPasswordException() {}
      public MissingPasswordException(String message) { super(message); }
    }

    protected class MissingUsernameException extends Exception {
      public MissingUsernameException() {};
      public MissingUsernameException(String message) { super(message); }
    }

    protected class BadSubjectException extends Exception {
      public BadSubjectException() {}
      public BadSubjectException(String message) { super(message); }
    }

    protected class NoSuchProviderException extends Exception {
      public NoSuchProviderException() {}
      public NoSuchProviderException(String name, String role, String topology) {
        super("Could not find provider with role: " + role + ", name: " + name + " inside of topology: " + topology);
      }
    }

    //    returns false if any errors are printed
    protected boolean hasShiroProviderErrors(Topology topology, boolean groupLookup) {
//      First let's define the variables that represent the ShiroProvider params
      String mainLdapRealm = "main.ldapRealm";
      String contextFactory = mainLdapRealm + ".contextFactory";
      String groupContextFactory = "main.ldapGroupContextFactory";
      String authorizationEnabled = mainLdapRealm + ".authorizationEnabled";
      String userSearchAttributeName = mainLdapRealm + ".userSearchAttributeName";
      String userObjectClass = mainLdapRealm + ".userObjectClass";
      String authenticationMechanism = mainLdapRealm + ".authenticationMechanism"; // Should not be used up to v0.6.0)
      String searchBase = mainLdapRealm + ".searchBase";
      String groupSearchBase = mainLdapRealm + ".groupSearchBase";
      String userSearchBase = mainLdapRealm + ".userSearchBase";
      String groupObjectClass = mainLdapRealm + ".groupObjectClass";
      String memberAttribute = mainLdapRealm + ".memberAttribute";
      String memberAttributeValueTemplate = mainLdapRealm + ".memberAttributeValueTemplate";
      String systemUsername = contextFactory + ".systemUsername";
      String systemPassword = contextFactory + ".systemPassword";
      String url = contextFactory + ".url";
      String userDnTemplate = mainLdapRealm + ".userDnTemplate";


      Provider shiro = topology.getProvider("authentication", "ShiroProvider");
      if(shiro != null) {
        Map<String, String> params = shiro.getParams();
        int errs = 0;
        if(groupLookup) {
          int errors = 0;
          errors += hasParam(params, groupContextFactory, true) ? 0 : 1;
          errors += hasParam(params, groupObjectClass, true) ? 0 : 1;
          errors += hasParam(params, memberAttributeValueTemplate, true) ? 0 : 1;
          errors += hasParam(params, memberAttribute, true) ? 0 : 1;
          errors += hasParam(params, authorizationEnabled, true) ? 0 : 1;
          errors += hasParam(params, systemUsername, true) ? 0 : 1;
          errors += hasParam(params, systemPassword, true) ? 0 : 1;
          errors += hasParam(params, userSearchBase, true) ? 0 : 1;
          errors += hasParam(params, groupSearchBase, true) ? 0 : 1;
          errs += errors;

        } else {

//        Realm + Url is always required.
          errs += hasParam(params, mainLdapRealm, true) ? 0 : 1;
          errs += hasParam(params, url, true) ? 0 : 1;

          if(hasParam(params, authorizationEnabled, false)) {
            int errors = 0;
            int searchBaseErrors = 0;
            errors += hasParam(params, systemUsername, true) ? 0 : 1;
            errors += hasParam(params, systemPassword, true) ? 0 : 1;
            searchBaseErrors += hasParam(params, searchBase, false) ? 0 : hasParam(params, userSearchBase, false) ? 0 : 1;
            if (searchBaseErrors > 0) {
              out.println("Warn: Both " + searchBase + " and " + userSearchBase + " are missing from the topology");
            }
            errors += searchBaseErrors;
            errs += errors;
          }

//        If any one of these is present they must all be present
          if( hasParam(params, userSearchAttributeName, false) ||
              hasParam(params, userObjectClass, false) ||
              hasParam(params, searchBase, false) ||
              hasParam(params, userSearchBase, false)) {

            int errors = 0;
            errors += hasParam(params, userSearchAttributeName, true) ? 0 : 1;
            errors += hasParam(params, userObjectClass, true) ? 0 : 1;
            errors += hasParam(params, searchBase, false) ? 0 : hasParam(params, userSearchBase, false) ? 0 : 1;
            errors += hasParam(params, systemUsername, true) ? 0 : 1;
            errors += hasParam(params, systemPassword, true) ? 0 : 1;

            if(errors > 0) {
              out.println(userSearchAttributeName + " or " + userObjectClass + " or " + searchBase + " or " + userSearchBase + " was found in the topology");
              out.println("If any one of the above params is present then " + userSearchAttributeName + 
                  " and " + userObjectClass + " must both be present and either " + searchBase + " or " + userSearchBase + " must also be present.");
            }
            errs += errors;
          } else {
            errs += hasParam(params, userDnTemplate, true) ?  0 : 1;

          }
        }
        return (errs > 0);
      } else {
        out.println("Could not obtain ShiroProvider");
        return true;
      }
    }

    // Checks to see if the param name is present. If not, notify the user
    protected boolean hasParam(Map<String, String> params, String key, boolean notifyUser){
      if(params.get(key) == null){
        if(notifyUser) { out.println("Warn: " + key + " is not present in topology"); }
        return false;
      } else { return true; }
    }

    /**
     *
     * @param ini - the path to the shiro.ini file within a topology deployment.
     * @param token - token for username and password
     * @return - true/false whether a user was successfully able to authenticate or not.
     */
    protected boolean authenticateUser(Ini ini, UsernamePasswordToken token){
      boolean result = false;
      try {
        Subject subject = getSubject(ini);
        try{
          subject.login(token);
          if(subject.isAuthenticated()){
            result = true;
          }
        } catch (AuthenticationException e){
          out.println(e.toString());
          out.println(e.getCause().getMessage());
          if (debug) {
            e.printStackTrace(out);
          } else {
            out.println(debugMessage);
          }
        } finally {
          subject.logout();
        }
      } catch (BadSubjectException e) {
        out.println(e.toString());
        if (debug){
          e.printStackTrace();
        } else {
          out.println(debugMessage);
        }
      } catch (ConfigurationException e) {
        out.println(e.toString());
      } catch ( Exception e ) {
        out.println(e.getCause());
        out.println(e.toString());
      }
      return result;
    }

    protected boolean authenticateUser(String config, UsernamePasswordToken token) throws ConfigurationException {
      Ini ini = new Ini();
      try {
        ini.loadFromPath(config);
        return authenticateUser(ini, token);
      } catch (ConfigurationException e) {
        throw e;
      }
    }

    /**
     *
     * @param userDn - fully qualified userDn used for LDAP authentication
     * @return - returns the principal found in the userDn after "uid="
     */
    protected String getPrincipal(String userDn){
      String result = "";

//      Need to determine whether we are using AD or LDAP?
//      LDAP userDn usually starts with "uid="
//      AD userDn usually starts with cn/CN
//      Find the userDN template

      try {
        Topology t = getTopology(cluster);
        Provider shiro = t.getProvider("authentication", "ShiroProvider");

        String p1 = shiro.getParams().get("main.ldapRealm.userDnTemplate");

//        We know everything between first "=" and "," will be part of the principal.
        int eq = userDn.indexOf("=");
        int com = userDn.indexOf(",");
        if(eq != -1 && com > eq && com != -1) {
          result = userDn.substring(eq + 1, com);
        } else {
          result = "";
        }
      } catch (NoSuchTopologyException e) {
        out.println(e.toString());
        result = userDn;
      } finally {
        return result;
      }
    }

    /**
     *
     * @param t - topology configuration to use
     * @param config - the path to the shiro.ini file from the topology deployment.
     * @return - true/false whether LDAP successfully authenticated with system credentials.
     */
    protected boolean testSysBind(Topology t, String config) {
      boolean result = false;
      String username;
      char[] password;

      try {
//        Pull out contextFactory.url param for light shiro config
        Provider shiro = t.getProvider("authentication", "ShiroProvider");
        Map<String, String> params = shiro.getParams();
        String url = params.get("main.ldapRealm.contextFactory.url");

//        Build the Ini with minimum requirements
        Ini ini = new Ini();
        ini.addSection("main");
        ini.setSectionProperty("main", "ldapRealm", "org.apache.knox.gateway.shirorealm.KnoxLdapRealm");
        ini.setSectionProperty("main", "ldapContextFactory", "org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory");
        ini.setSectionProperty("main", "ldapRealm.contextFactory.url", url);

        username = getSystemUsername(t);
        password = getSystemPassword(t);
        result = authenticateUser(ini, new UsernamePasswordToken(username, password));
      } catch (MissingUsernameException | NoSuchProviderException | MissingPasswordException e) {
        out.println(e.toString());
      } catch (NullPointerException e) {
        out.println(e.toString());
      }
      return result;
    }

    /**
     *
     * @param t - topology configuration to use
     * @return - the principal of the systemUsername specified in topology. null if non-existent
     */
    private String getSystemUsername(Topology t) throws MissingUsernameException, NoSuchProviderException {
      final String SYSTEM_USERNAME = "main.ldapRealm.contextFactory.systemUsername";
      String user = null;
      Provider shiroProvider = t.getProvider("authentication", "ShiroProvider");
      if(shiroProvider != null){
        Map<String, String> params = shiroProvider.getParams();
        String userDn = params.get(SYSTEM_USERNAME);
        user = userDn;
      } else {
        throw new NoSuchProviderException("ShiroProvider", "authentication", t.getName());
      }
      return user;
    }

    /**
     *
     * @param t - topology configuration to use
     * @return - the systemPassword specified in topology. null if non-existent
     */
    private char[] getSystemPassword(Topology t) throws NoSuchProviderException, MissingPasswordException{
      final String SYSTEM_PASSWORD = "main.ldapRealm.contextFactory.systemPassword";
      String pass = null;
      Provider shiro = t.getProvider("authentication", "ShiroProvider");
      if(shiro != null){
        Map<String, String> params = shiro.getParams();
        pass = params.get(SYSTEM_PASSWORD);
      } else {
        throw new NoSuchProviderException("ShiroProvider", "authentication", t.getName());
      }

      if(pass != null) {
        return pass.toCharArray();
      } else {
        throw new MissingPasswordException("ShiroProvider did not contain param: " + SYSTEM_PASSWORD);
      }
    }

    /**
     *
     * @param config - the shiro.ini config file created in topology deployment.
     * @return returns the Subject given by the shiro config's settings.
     */
    protected Subject getSubject(Ini config) throws BadSubjectException {
      try {
        ThreadContext.unbindSubject();
        Factory factory = new IniSecurityManagerFactory(config);
        org.apache.shiro.mgt.SecurityManager securityManager = (org.apache.shiro.mgt.SecurityManager) factory.getInstance();
        SecurityUtils.setSecurityManager(securityManager);
        Subject subject = SecurityUtils.getSubject();
        if( subject != null) {
          return subject;
        } else {
          out.println("Error Creating Subject from config at: " + config);
        }
      } catch (Exception e){
        out.println(e.toString());
      }
      throw new BadSubjectException("Subject could not be created with Shiro Config at " + config);
    }

    protected Subject getSubject(String config) throws ConfigurationException {
      Ini ini = new Ini();
      ini.loadFromPath(config);
      try {
        return getSubject(ini);
      } catch (BadSubjectException e) {
        throw new ConfigurationException("Could not get Subject with Ini at " + config);
      }
    }

    /**
     * prompts the user for credentials in the command line if necessary
     * populates the username and password members.
     */
    protected void promptCredentials() {
      if(this.username == null){
        Console c = System.console();
        if( c != null) {
          this.username = c.readLine("Username: ");
        }else{
          try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            out.println("Username: ");
            this.username = reader.readLine();
            reader.close();
          } catch (IOException e){
            out.println(e.toString());
            this.username = "";
          }
        }
      }

      if(this.password == null){
        Console c = System.console();
        if( c != null) {
          this.password = c.readPassword("Password: ");
        }else{
          try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            out.println("Password: ");
            String pw = reader.readLine();
            if(pw != null){
              this.password = pw.toCharArray();
            } else {
              this.password = new char[0];
            }
            reader.close();
          } catch (IOException e){
            out.println(e.toString());
            this.password = new char[0];
          }
        }
      }
    }

    /**
     *
     * @param topologyName - the name of the topology to retrieve
     * @return - Topology object with specified name. null if topology doesn't exist in TopologyService
     */
    protected Topology getTopology(String topologyName) throws NoSuchTopologyException {
      TopologyService ts = getTopologyService();
      ts.reloadTopologies();
      for (Topology t : ts.getTopologies()) {
        if(t.getName().equals(topologyName)) {
          return t;
        }
      }
      throw new  NoSuchTopologyException("Topology " + topologyName + " does not" +
          " exist in the topologies directory.");
    }

    /**
     *
     * @param t - Topology to use for config
     * @return - path of shiro.ini config file.
     */
    protected String getConfig(Topology t){
      File tmpDir = new File(System.getProperty("java.io.tmpdir"));
      DeploymentFactory.setGatewayServices(services);
      EnterpriseArchive archive = DeploymentFactory.createDeployment(getGatewayConfig(), t);
      File war = archive.as(ExplodedExporter.class).exportExploded(tmpDir, t.getName() + "_deploy.tmp");
      war.deleteOnExit();
      String config = war.getAbsolutePath() + "/%2F/WEB-INF/shiro.ini";
      try{
        FileUtils.forceDeleteOnExit(war);
      } catch (IOException e) {
        out.println(e.toString());
        war.deleteOnExit();
      }
      return config;
    }

    /**
     * populates username and password if they were passed as arguments, if not will prompt user for them.
     */
    void acquireCredentials(){
      if(user != null){
        this.username = user;
      }
      if(pass != null){
        this.password = pass.toCharArray();
      }
      promptCredentials();
    }

    /**
     *
     * @return - true or false if the topology was acquired from the topology service and populated in the topology
     * field.
     */
    protected boolean acquireTopology(){
      try {
        topology = getTopology(cluster);
      } catch (NoSuchTopologyException e) {
        out.println(e.toString());
        return false;
      }
      return true;
    }
  }

  private class LDAPAuthCommand extends LDAPCommand {

    public static final String USAGE = "user-auth-test [--cluster clustername] [--u username] [--p password] [--g]";
    public static final String DESC = "This command tests a cluster's configuration ability to\n " +
        "authenticate a user with a cluster's ShiroProvider settings.\n Use \"--g\" if you want to list the groups a" +
        " user is a member of. \nOptional: [--u username]: Provide a username argument to the command\n" +
        "Optional: [--p password]: Provide a password argument to the command.\n" +
        "If a username and password argument are not supplied, the terminal will prompt you for one.";

    private static final String  SUBJECT_USER_GROUPS = "subject.userGroups";
    private HashSet<String> groupSet = new HashSet<>();

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    @Override
    public void execute() {
      if(!acquireTopology()){
        return;
      }
      acquireCredentials();

      if(topology.getProvider("authentication", "ShiroProvider") == null) {
        out.println("ERR: This tool currently only works with Shiro as the authentication provider.");
        out.println("Please update the topology to use \"ShiroProvider\" as the authentication provider.");
        return;
      }

      String config = getConfig(topology);

      if(new File(config).exists()) {
          if(authenticateUser(config, new UsernamePasswordToken(username, password))) {
            out.println("LDAP authentication successful!");
            if(groups) {
              if(testSysBind(topology, config)) {
                groupSet = getGroups(topology, new UsernamePasswordToken(username, password));
                if(groupSet == null || groupSet.isEmpty()) {
                  out.println(username + " does not belong to any groups");
                  if(groups) {
                    hasShiroProviderErrors(topology, true);
                    out.println("You were looking for this user's groups but this user does not belong to any.");
                    out.println("Your topology file may be incorrectly configured for group lookup.");
                  }
                } else {
                  for (Object o : groupSet.toArray()) {
                    out.println(username + " is a member of: " + o.toString());
                  }
                }
              }
            }
          } else {
            out.println("ERR: Unable to authenticate user: " + username);
          }
      } else {
        out.println("ERR: No shiro config file found.");
      }
    }

    private HashSet<String> getGroups(Topology t, UsernamePasswordToken token){
      HashSet<String> groups = null;
      try {
        Subject subject = getSubject(getConfig(t));
        if(!subject.isAuthenticated()) {
          subject.login(token);
        }
        subject.hasRole(""); //Populate subject groups
        groups = (HashSet) subject.getSession().getAttribute(SUBJECT_USER_GROUPS);
        subject.logout();
      } catch (AuthenticationException e) {
        out.println("Error retrieving groups");
        out.println(e.toString());
        if(debug) {
          e.printStackTrace();
        } else {
          out.println(debugMessage);
        }
      } catch (ConfigurationException e) {
        out.println(e.toString());
        if(debug){
          e.printStackTrace();
        }
      }
      return groups;
    }

  }

  public class LDAPSysBindCommand extends LDAPCommand {

    public static final String USAGE = "system-user-auth-test [--cluster clustername] [--d]";
    public static final String DESC = "This command tests a cluster configuration's ability to\n " +
        "authenticate a user with a cluster's ShiroProvider settings.";

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    @Override
    public void execute() {

      if(!acquireTopology()) {
        return;
      }

      if(hasShiroProviderErrors(topology, false)) {
        out.println("Topology warnings present. SystemUser may not bind.");
      }

      if(testSysBind(topology, getConfig(topology))) {
        out.println("System LDAP Bind successful.");
      } else {
        out.println("Unable to successfully bind to LDAP server with topology credentials. Are your parameters correct?");
      }
    }
  }

  private GatewayConfig getGatewayConfig() {
    GatewayConfig result;
    Configuration conf = getConf();
    if(conf != null && conf instanceof GatewayConfig) {
      result = (GatewayConfig) conf;
    } else {
      result = new GatewayConfigImpl();
    }
    return result;
  }

  public class ServiceTestCommand extends Command {
    public static final String USAGE = "service-test [--u username] [--p password] [--cluster clustername] [--hostname name] " +
        "[--port port]";
    public static final String DESC =
                        "This command requires a running instance of Knox to be present on the same machine.\n" +
                        "It will execute a test to make sure all services are accessible through the gateway URLs.\n" +
                        "Errors are reported and suggestions to resolve any problems are returned. JSON formatted.\n";

    private boolean ssl = true;
    private int attempts = 0;

    @Override
    public String getUsage() { return USAGE + ":\n\n" + DESC; };

    @Override
    public void execute() {
      attempts++;
      SSLContext ctx = null;
      CloseableHttpClient client;
      String http = "http://";
      String https = "https://";
      GatewayConfig conf = getGatewayConfig();
      String gatewayPort;
      String host;


      if(cluster == null) {
        printKnoxShellUsage();
        out.println("A --cluster argument is required.");
        return;
      }

      if(hostname != null) {
        host = hostname;
      } else {
        try {
          host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
          out.println(e.toString());
          out.println("Defaulting address to localhost. Use --hostname option to specify a different hostname");
          host = "localhost";
        }
      }

      if (port != null) {
        gatewayPort = port;
      } else if (conf.getGatewayPort() > -1) {
        gatewayPort = Integer.toString(conf.getGatewayPort());
      } else {
        out.println("Could not get port. Please supply it using the --port option");
        return;
      }


      String path = "/" + conf.getGatewayPath();
      String topology = "/" + cluster;
      String httpServiceTestURL = http + host + ":" + gatewayPort + path + topology + "/service-test";
      String httpsServiceTestURL = https + host + ":" + gatewayPort + path + topology + "/service-test";

      String authString = "";
//    Create Authorization String
      if( user != null && pass != null) {
        authString = "Basic " + Base64.encodeBase64String((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
      } else {
        out.println("Username and/or password not supplied. Expect HTTP 401 Unauthorized responses.");
      }

//    Attempt to build SSL context for HTTP client.
      try {
        ctx = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
      } catch (Exception e) {
        out.println(e.toString());
      }

//    Initialize the HTTP client
      if(ctx == null) {
        client = HttpClients.createDefault();
      } else {
        client = HttpClients.custom().setSslcontext(ctx).build();
      }

      HttpGet request;
      if(ssl) {
        request = new HttpGet(httpsServiceTestURL);
      } else {
        request = new HttpGet(httpServiceTestURL);
      }


      request.setHeader("Authorization", authString);
      request.setHeader("Accept", MediaType.APPLICATION_JSON.getMediaType());
      try {
        out.println(request.toString());
        CloseableHttpResponse response = client.execute(request);

        switch (response.getStatusLine().getStatusCode()) {

          case 200:
            response.getEntity().writeTo(out);
            break;
          case 404:
            out.println("Could not find service-test resource");
            out.println("Make sure you have configured the SERVICE-TEST service in your topology.");
            break;
          case 500:
            out.println("HTTP 500 Server error");
            break;

          default:
            out.println("Unexpected HTTP response code.");
            out.println(response.getStatusLine().toString());
            response.getEntity().writeTo(out);
            break;
        }

        response.close();
        request.releaseConnection();

      } catch (ClientProtocolException e) {
        out.println(e.toString());
        if (debug) {
          e.printStackTrace(out);
        }
      } catch (SSLException e) {
        out.println(e.toString());
        retryRequest();
      } catch (IOException e) {
        out.println(e.toString());
        retryRequest();
        if(debug) {
          e.printStackTrace(out);
        }
      } finally {
        try {
          client.close();
        } catch (IOException e) {
          out.println(e.toString());
        }
      }

    }

    public void retryRequest(){
      if(attempts < 2) {
        if(ssl) {
          ssl = false;
          out.println("Attempting request without SSL.");
        } else {
          ssl = true;
          out.println("Attempting request with SSL ");
        }
        execute();
      } else {
        out.println("Unable to successfully make request. Try using the API with cURL.");
      }
    }

  }

  public class RemoteRegistryClientsListCommand extends Command {

    static final String USAGE = "list-registry-clients";
    static final String DESC = "Lists all of the remote configuration registry clients defined in gateway-site.xml.\n";

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
     */
    @Override
    public void execute() throws Exception {
      GatewayConfig config = getGatewayConfig();
      List<String> remoteConfigRegistryClientNames = config.getRemoteRegistryConfigurationNames();
      if (!remoteConfigRegistryClientNames.isEmpty()) {
        out.println("Listing remote configuration registry clients:");
        for (String name : remoteConfigRegistryClientNames) {
          out.println(name);
        }
      }
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
     */
    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
 }

  private abstract class RemoteRegistryCommand extends Command {
    static final String ROOT_ENTRY = "/knox";
    static final String CONFIG_ENTRY = ROOT_ENTRY + "/config";
    static final String PROVIDER_CONFIG_ENTRY = CONFIG_ENTRY + "/shared-providers";
    static final String DESCRIPTORS_ENTRY = CONFIG_ENTRY + "/descriptors";

    protected RemoteConfigurationRegistryClient getClient() {
      RemoteConfigurationRegistryClient client = null;
      if (remoteRegistryClient != null) {
        RemoteConfigurationRegistryClientService cs = getRemoteConfigRegistryClientService();
        client = cs.get(remoteRegistryClient);
        if (client == null) {
          out.println("No remote configuration registry identified by '" + remoteRegistryClient + "' could be found.");
        }
      } else {
        out.println("Missing required argument : --registry-client\n");
      }
      return client;
    }
  }


  public class RemoteRegistryListProviderConfigsCommand extends RemoteRegistryCommand {
    static final String USAGE = "list-provider-configs --registry-client name";
    static final String DESC = "Lists the provider configurations present in the specified remote registry\n";

    @Override
    public void execute() {
      RemoteConfigurationRegistryClient client = getClient();
      if (client != null) {
        out.println("Provider Configurations (@" + client.getAddress() + ")");
        List<String> entries = client.listChildEntries(PROVIDER_CONFIG_ENTRY);
        if (entries != null) {
          for (String entry : entries) {
            out.println(entry);
          }
        }
        out.println();
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  public class RemoteRegistryListDescriptorsCommand extends RemoteRegistryCommand {
    static final String USAGE = "list-descriptors --registry-client name";
    static final String DESC = "Lists the descriptors present in the specified remote registry\n";

    @Override
    public void execute() {
      RemoteConfigurationRegistryClient client = getClient();
      if (client != null) {
        out.println("Descriptors (@" + client.getAddress() + ")");
        List<String> entries = client.listChildEntries(DESCRIPTORS_ENTRY);
        if (entries != null) {
          for (String entry : entries) {
            out.println(entry);
          }
        }
        out.println();
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  /**
   * Base class for remote config registry upload commands
   */
  public abstract class RemoteRegistryUploadCommand extends RemoteRegistryCommand {
    private File sourceFile = null;
    protected String filename = null;

    protected RemoteRegistryUploadCommand(String sourceFileName) {
      this.filename = sourceFileName;
    }

    private void upload(RemoteConfigurationRegistryClient client, String entryPath, File source) throws Exception {
      String content = FileUtils.readFileToString(source, StandardCharsets.UTF_8);
      if (client.entryExists(entryPath)) {
        // If it exists, then we're going to set the data
        client.setEntryData(entryPath, content);
      } else {
        // If it does not exist, then create it and set the data
        client.createEntry(entryPath, content);
      }
    }

    File getSourceFile() {
      if (sourceFile == null) {
        sourceFile = new File(filename);
      }
      return sourceFile;
    }

    String getEntryName(String prefixPath) {
      String entryName = remoteRegistryEntryName;
      if (entryName == null) {
        File sourceFile = getSourceFile();
        if (sourceFile.exists()) {
          String path = sourceFile.getAbsolutePath();
          entryName = path.substring(path.lastIndexOf(File.separator) + 1);
        } else {
          out.println("Could not locate source file: " + filename);
        }
      }
      return prefixPath + "/" + entryName;
    }

    protected void execute(String entryName, File sourceFile) throws Exception {
      RemoteConfigurationRegistryClient client = getClient();
      if (client != null) {
        if (entryName != null) {
          upload(client, entryName, sourceFile);
        }
      }
    }
  }


  public class RemoteRegistryUploadProviderConfigCommand extends RemoteRegistryUploadCommand {

    static final String USAGE = "upload-provider-config providerConfigFile --registry-client name [--entry-name entryName]";
    static final String DESC = "Uploads a provider configuration to the specified remote registry client, optionally " +
                               "renaming the entry.\nIf the entry name is not specified, the name of the uploaded " +
                               "file is used.\n";

    RemoteRegistryUploadProviderConfigCommand(String fileName) {
      super(fileName);
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
     */
    @Override
    public void execute() throws Exception {
      super.execute(getEntryName(PROVIDER_CONFIG_ENTRY), getSourceFile());
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
     */
    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  public class RemoteRegistryUploadDescriptorCommand extends RemoteRegistryUploadCommand {

    static final String USAGE = "upload-descriptor descriptorFile --registry-client name [--entry-name entryName]";
    static final String DESC = "Uploads a simple descriptor using the specified remote registry client, optionally " +
                               "renaming the entry.\nIf the entry name is not specified, the name of the uploaded " +
                               "file is used.\n";

    RemoteRegistryUploadDescriptorCommand(String fileName) {
      super(fileName);
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
     */
    @Override
    public void execute() throws Exception {
      super.execute(getEntryName(DESCRIPTORS_ENTRY), getSourceFile());
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
     */
    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  public class RemoteRegistryGetACLCommand extends RemoteRegistryCommand {

    static final String USAGE = "get-registry-acl entry --registry-client name";
    static final String DESC = "Presents the ACL settings for the specified remote registry entry.\n";

    private String entry = null;

    RemoteRegistryGetACLCommand(String entry) {
      this.entry = entry;
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#execute()
     */
    @Override
    public void execute() throws Exception {
      RemoteConfigurationRegistryClient client = getClient();
      if (client != null) {
        if (entry != null) {
          List<RemoteConfigurationRegistryClient.EntryACL> acls = client.getACL(entry);
          for (RemoteConfigurationRegistryClient.EntryACL acl : acls) {
            out.println(acl.getType() + ":" + acl.getId() + ":" + acl.getPermissions());
          }
        }
      }
    }

    /* (non-Javadoc)
     * @see org.apache.knox.gateway.util.KnoxCLI.Command#getUsage()
     */
    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  /**
   * Base class for remote config registry delete commands
   */
  public abstract class RemoteRegistryDeleteCommand extends RemoteRegistryCommand {
    protected String entryName = null;

    protected RemoteRegistryDeleteCommand(String entryName) {
      this.entryName = entryName;
    }

    private void delete(RemoteConfigurationRegistryClient client, String entryPath) throws Exception {
      if (client.entryExists(entryPath)) {
        // If it exists, then delete it
        client.deleteEntry(entryPath);
      }
    }

    protected void execute(String entryName) throws Exception {
      RemoteConfigurationRegistryClient client = getClient();
      if (client != null) {
        if (entryName != null) {
          delete(client, entryName);
        }
      }
    }
  }


  public class RemoteRegistryDeleteProviderConfigCommand extends RemoteRegistryDeleteCommand {
    static final String USAGE = "delete-provider-config providerConfig --registry-client name";
    static final String DESC = "Deletes a shared provider configuration from the specified remote registry.\n";

    public RemoteRegistryDeleteProviderConfigCommand(String entryName) {
      super(entryName);
    }

    @Override
    public void execute() throws Exception {
      execute(PROVIDER_CONFIG_ENTRY + "/" + entryName);
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  public class RemoteRegistryDeleteDescriptorCommand extends RemoteRegistryDeleteCommand {
    static final String USAGE = "delete-descriptor descriptor --registry-client name";
    static final String DESC = "Deletes a simple descriptor from the specified remote registry.\n";

    public RemoteRegistryDeleteDescriptorCommand(String entryName) {
      super(entryName);
    }

    @Override
    public void execute() throws Exception {
      execute(DESCRIPTORS_ENTRY + "/" + entryName);
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  private static Properties loadBuildProperties() {
    Properties properties = new Properties();
    InputStream inputStream = KnoxCLI.class.getClassLoader().getResourceAsStream( "build.properties" );
    if( inputStream != null ) {
      try {
        properties.load( inputStream );
        inputStream.close();
      } catch( IOException e ) {
        // Ignore.
      }
    }
    return properties;
  }

  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    PropertyConfigurator.configure( System.getProperty( "log4j.configuration" ) );
    int res = ToolRunner.run(new GatewayConfigImpl(), new KnoxCLI(), args);
    System.exit(res);
  }
}
