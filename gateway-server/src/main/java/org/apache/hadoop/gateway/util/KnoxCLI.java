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
package org.apache.hadoop.gateway.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.gateway.GatewayCommandLine;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.config.impl.GatewayConfigImpl;
import org.apache.hadoop.gateway.services.CLIGatewayServices;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.Service;
import org.apache.hadoop.gateway.services.ServiceLifecycleException;
import org.apache.hadoop.gateway.services.topology.TopologyService;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.KeystoreService;
import org.apache.hadoop.gateway.services.security.KeystoreServiceException;
import org.apache.hadoop.gateway.topology.Topology;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 *
 */
public class KnoxCLI extends Configured implements Tool {

  private static final String USAGE_PREFIX = "KnoxCLI {cmd} [options]";
  final static private String COMMANDS =
      "   [--help]\n" +
      "   [" + VersionCommand.USAGE + "]\n" +
      "   [" + MasterCreateCommand.USAGE + "]\n" +
      "   [" + CertCreateCommand.USAGE + "]\n" +
      "   [" + AliasCreateCommand.USAGE + "]\n" +
      "   [" + AliasDeleteCommand.USAGE + "]\n" +
      "   [" + AliasListCommand.USAGE + "]\n" +
      "   [" + RedeployCommand.USAGE + "]\n";

  /** allows stdout to be captured if necessary */
  public PrintStream out = System.out;
  /** allows stderr to be captured if necessary */
  public PrintStream err = System.err;
  
  private static GatewayServices services = new CLIGatewayServices();
  private Command command;
  private String value = null;
  private String cluster = null;
  private String generate = "false";
  private String hostname = null;
  private boolean force = false;
  
  // For testing only
  private String master = null;

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
      if (command.validate()) {
          initializeServices( command instanceof MasterCreateCommand );
          command.execute();
      } else {
        exitCode = -2;
      }
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
    GatewayConfig config = new GatewayConfigImpl();
    Map<String,String> options = new HashMap<String,String>();
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
   * % knoxcli master-create keyName [--size size] [--generate]
   * % knoxcli create-alias alias [--cluster clustername] [--generate] [--value v]
   * % knoxcli list-alias [--cluster clustername]
   * % knoxcli delete=alias alias [--cluster clustername]
   * % knoxcli create-cert alias [--hostname h]
   * % knoxcli redeploy [--cluster clustername]
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
      } else if ( args[i].equals("--cluster") || args[i].equals("--topology") ) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.cluster = args[++i];
      } else if (args[i].equals("--generate")) {
        if ( command != null && command instanceof MasterCreateCommand ) {
          this.master = UUID.randomUUID().toString();
        } else {
          this.generate = "true";
        }
      } else if (args[i].equals("--hostname")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.hostname = args[++i];
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
    }
  }

  private abstract class Command {
    protected Service provider = null;

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
  }
  
 private class AliasListCommand extends Command {

  public static final String USAGE = "list-alias [--cluster clustername]";
  public static final String DESC = "The list-alias command lists all of the aliases\n" +
                                    "for the given hadoop --cluster. The default\n" +
                                    "--cluster being the gateway itself.";

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#execute()
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
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#getUsage()
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
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#execute()
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
         as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
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
         ks.addSelfSignedCertForGateway("gateway-identity", passphrase, hostname);
//         logAndValidateCertificate();
         out.println("Certificate gateway-identity has been successfully created.");
       } catch (KeystoreServiceException e) {
         throw new ServiceLifecycleException("Keystore was not loaded properly - the provided (or persisted) master secret may not match the password for the keystore.", e);
       }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }

 }

 public class AliasCreateCommand extends Command {

  public static final String USAGE = "create-alias aliasname [--cluster clustername] " +
                                     "[ (--value v) | (--generate) ]";
  public static final String DESC = "The create-alias command will create an alias\n" +
                                    "and secret pair within the credential store for the\n" +
                                    "indicated --cluster otherwise within the gateway\n" +
                                    "credential store. The actual secret may be specified via\n" +
                                    "the --value option or --generate will create a random secret\n" +
                                    "for you.";
  
  private String name = null; 

  /**
    * @param alias
    */
   public AliasCreateCommand(String alias) {
     name = alias;
   }

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#execute()
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
       if (generate.equals("true")) {
         as.generateAliasForCluster(cluster, name);
         out.println(name + " has been successfully generated.");
       }
       else {
         throw new IllegalArgumentException("No value has been set. " +
         		"Consider setting --generate or --value.");
       }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#getUsage()
    */
   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
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
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#execute()
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
          as.removeAliasForCluster(cluster, name);
          out.println(name + " has been successfully deleted.");
        } else {
          out.println("Invalid cluster name provided: " + cluster);
        }
     }
   }

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#getUsage()
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
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#execute()
    */
   @Override
   public void execute() throws Exception {
     out.println("Master secret has been persisted to disk.");
   }

   /* (non-Javadoc)
    * @see org.apache.hadoop.gateway.util.KnoxCLI.Command#getUsage()
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
          String.format(
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
