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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
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
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.token.TokenMigrationTarget;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.validation.TopologyValidator;
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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;

public class KnoxCLI extends Configured implements Tool {

  private static final Collection<String> SUPPORTED_JWK_ALGORITHMS = Stream
      .of(JWSAlgorithm.HS256.getName(), JWSAlgorithm.HS384.getName(), JWSAlgorithm.HS512.getName()).collect(Collectors.toSet());
  private static final String ALIAS_PREFIX = "${ALIAS=";
  private static final String USAGE_PREFIX = "KnoxCLI {cmd} [options]";
  private static final String COMMANDS =
      "   [--help]\n" +
      "   [" + VersionCommand.USAGE + "]\n" +
      "   [" + MasterCreateCommand.USAGE + "]\n" +
      "   [" + CertCreateCommand.USAGE + "]\n" +
      "   [" + CertExportCommand.USAGE + "]\n" +
      "   [" + AliasCreateCommand.USAGE + "]\n" +
      "   [" + BatchAliasCreateCommand.USAGE + "]\n" +
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
      "   [" + RemoteRegistryGetACLCommand.USAGE + "]\n" +
      "   [" + TopologyConverter.USAGE + "]\n" +
      "   [" + JWKGenerator.USAGE  + "]\n" +
      "   [" + GenerateDescriptorCommand.USAGE + "]\n" +
      "   [" + TokenMigration.USAGE  + "]\n" +
      "   [" + CreateListAliasesCommand.USAGE + "]\n";
  private static final String CLUSTER_STRING_SEPARATOR = ",";

  /** allows stdout to be captured if necessary */
  public PrintStream out = System.out;
  /** allows stderr to be captured if necessary */
  public PrintStream err = System.err;

  private static GatewayServices services = new CLIGatewayServices();
  private Command command;
  private String value;
  private String cluster;
  private String path;
  private String generate = "false";
  private String hostname;
  private String port;
  private boolean force;
  private boolean debug;
  private String user;
  private String pass;
  private boolean groups;
  private JWSAlgorithm jwsAlgorithm = JWSAlgorithm.HS256;
  private int progressCount = 10;
  private boolean archiveMigratedTokens;
  private boolean migrateExpiredTokens;
  private boolean verbose;
  private String alias;

  private String remoteRegistryClient;
  private String remoteRegistryEntryName;

  private String type;
  private String topologyName;
  private String providerName;
  private String descriptorName;
  private String outputDir;
  private String discoveryUrl;
  private String discoveryUser;
  private String discoveryPasswordAlias;
  private String discoveryType;
  private String serviceName;
  private String urlsFilePath;
  private final Map<String, String> params = new TreeMap<>();

  // For testing only
  private String master;

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

  public static synchronized GatewayServices getGatewayServices() {
    return services;
  }

  private void initializeServices(boolean persisting) throws ServiceLifecycleException {
    GatewayConfig config = getGatewayConfig();
    if (config.isHadoopKerberosSecured()) {
      configureKerberosSecurity(config);
    }
    Map<String,String> options = new HashMap<>();
    options.put(GatewayCommandLine.PERSIST_LONG, Boolean.toString(persisting));
    if (master != null) {
      options.put("master", master);
    }
    services.init(config, options);
  }

  /**
   * Parse the command line arguments and initialize the data.
   *
   * See the usage information for the commands itself for information on which parameters they take.
   *
   * @param args command line arguments
   * @return return exit code
   * @throws IOException exception on starting KnoxCLI
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
        if (alias == null || "--help".equals(alias)) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("create-alias")) {
        String alias = null;
        if (args.length >= 2) {
          alias = args[++i];
        }
        command = new AliasCreateCommand(alias);
        if (alias == null || "--help".equals(alias)) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("create-aliases")) {
        command = new BatchAliasCreateCommand();
        if (args.length < 3 || "--help".equals(alias)) {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equals("create-list-aliases")) {
        command = new CreateListAliasesCommand();
        if (args.length < 3 || "--help".equals(alias)) {
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
        if (i + 1 >= args.length
                || "--generate".equals(args[i + 1]) // missing value
                || "--cluster".equals(args[i + 1])
                || "--master".equals(args[i + 1])) {
          printKnoxShellUsage();
          return -1;
        }
        this.value = args[++i];
        if (command instanceof MasterCreateCommand) {
          this.master = this.value;
        } else if (command instanceof BatchAliasCreateCommand) {
          ((BatchAliasCreateCommand) command).addValue(value);
        }
      } else if (args[i].equals("--alias")) {
        if (command instanceof BatchAliasCreateCommand) {
          ((BatchAliasCreateCommand) command).addName(args[++i]);
        } else {
          printKnoxShellUsage();
          return -1;
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
      } else if ( args[i].equals("--cluster") || args[i].equals("--topology") ) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.cluster = args[++i];
        if(command instanceof CreateListAliasesCommand) {
          ((CreateListAliasesCommand) command).toMap(this.cluster);
        }
      } else if (args[i].equals("service-test")) {
        if( i + 1 >= args.length) {
          printKnoxShellUsage();
          return -1;
        } else {
          command = new ServiceTestCommand();
        }
      } else if (args[i].equals("--generate")) {
        if ( command instanceof MasterCreateCommand ) {
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
      } else if (args[i].equals("--provider-name")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.providerName = args[++i];
      } else if (args[i].equals("--topology-name")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.topologyName = args[++i];
      } else if (args[i].equals("--descriptor-name")) {
        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
          printKnoxShellUsage();
          return -1;
        }
        this.descriptorName = args[++i];
      } else if (args[i].equals("--service-name")) {
        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
          printKnoxShellUsage();
          return -1;
        }
        this.serviceName = args[++i];
      } else if (args[i].equals("--service-urls-file")) {
        if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
          printKnoxShellUsage();
          return -1;
        }
        this.urlsFilePath = args[++i];
      } else if (args[i].equals("--output-dir")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.outputDir = args[++i];
      } else if (args[i].equals("--discovery-url")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.discoveryUrl = args[++i];
      } else if (args[i].equals("--discovery-user")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.discoveryUser = args[++i];
      } else if (args[i].equals("--discovery-pwd-alias")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.discoveryPasswordAlias = args[++i];
      } else if (args[i].equals("--discovery-type")) {
        if( i+1 >= args.length || args[i+1].startsWith( "-" ) ) {
          printKnoxShellUsage();
          return -1;
        }
        this.discoveryType = args[++i];
      } else if (args[i].equals("--master")) {
        // For testing only
        if( i+1 >= args.length
                || "--generate".equals(args[i + 1]) // missing value
                || "--force".equals(args[i + 1])) {
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
      } else if (args[i].equalsIgnoreCase("convert-topology")) {
        if (args.length >= 5) {
          command = new TopologyConverter();
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equalsIgnoreCase("generate-descriptor")) {
        if (args.length >= 7) {
          command = new GenerateDescriptorCommand();
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equalsIgnoreCase("--param")) {
        if (i + 2 < args.length) {
          params.put(args[++i], args[++i]);
        } else {
          printKnoxShellUsage();
          return -1;
        }
      } else if (args[i].equalsIgnoreCase("generate-jwk")) {
        command = new JWKGenerator();
      } else if (args[i].equalsIgnoreCase("--jwkAlg")) {
        final String algName = args[++i];
        if (!SUPPORTED_JWK_ALGORITHMS.contains(algName)) {
          printKnoxShellUsage();
          return -1;
        } else {
          jwsAlgorithm = JWSAlgorithm.parse(algName);
        }
      } else if (args[i].equalsIgnoreCase("--saveAlias")) {
        alias = args[++i];
      } else if (args[i].equalsIgnoreCase("migrate-tokens") ) {
        command = new TokenMigration();
      } else if (args[i].equalsIgnoreCase("--progressCount") ) {
        progressCount = Integer.parseInt(args[++i]);
      } else if (args[i].equalsIgnoreCase("--archiveMigrated") ) {
        archiveMigratedTokens = Boolean.parseBoolean(args[++i]);
      } else if (args[i].equalsIgnoreCase("--migrateExpiredTokens") ) {
        migrateExpiredTokens = Boolean.parseBoolean(args[++i]);
      } else if (args[i].equalsIgnoreCase("--verbose") ) {
        verbose = Boolean.parseBoolean(args[++i]);
      } else {
        printKnoxShellUsage();
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
      out.println(TopologyConverter.USAGE + "\n\n" + TopologyConverter.DESC);
      out.println();
      out.println( div );
      out.println(JWKGenerator.USAGE + "\n\n" + JWKGenerator.DESC);
      out.println();
      out.println( div );
      out.println(BatchAliasCreateCommand.USAGE + "\n\n" + BatchAliasCreateCommand.DESC);
      out.println();
      out.println( div );
      out.println(CreateListAliasesCommand.USAGE + "\n\n" + CreateListAliasesCommand.DESC);
      out.println();
      out.println( div );
    }
  }

  private abstract class Command {

    public boolean validate() {
      return true;
    }

    protected Service getService(String serviceName) {

      return null;
    }

    public abstract void execute() throws Exception;

    public abstract String getUsage();

    protected AliasService getAliasService() {
      return services.getService(ServiceType.ALIAS_SERVICE);
    }

    protected KeystoreService getKeystoreService() {
      return services.getService(ServiceType.KEYSTORE_SERVICE);
    }

    protected TopologyService getTopologyService()  {
      return services.getService(ServiceType.TOPOLOGY_SERVICE);
    }

    protected RemoteConfigurationRegistryClientService getRemoteConfigRegistryClientService() {
      return services.getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE);
    }

  }

 private class AliasListCommand extends Command {

  public static final String USAGE = "list-alias [--cluster cluster1,clusterN]";
  public static final String DESC = "The list-alias command lists all of the aliases\n" +
                                    "for the given hadoop --cluster(s). The default\n" +
                                    "--cluster being the gateway itself.";

   @Override
   public void execute() throws Exception {
     AliasService as = getAliasService();
     KeystoreService keystoreService = getKeystoreService();

     if (cluster == null) {
       cluster = "__gateway";
     }
     String[] clusters = cluster.split(CLUSTER_STRING_SEPARATOR);
     for (String currentCluster : clusters) {
       boolean credentialStoreForClusterAvailable =
               keystoreService.isCredentialStoreForClusterAvailable(currentCluster);
       if (credentialStoreForClusterAvailable) {
         out.println("Listing aliases for: " + currentCluster);
         List<String> aliases = as.getAliasesForCluster(currentCluster);
         for (String alias : aliases) {
           out.println(alias);
         }
         out.println("\n" + aliases.size() + " items.");
       } else {
         out.println("Invalid cluster name provided: " + currentCluster);
       }
     }
   }

   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }
 }

  public class CertExportCommand extends Command {

    public static final String USAGE = "export-cert [--type PEM|JKS|JCEKS|PKCS12]";
    public static final String DESC = "The export-cert command exports the public certificate\n" +
                                      "from the a gateway.jks keystore with the alias of gateway-identity.\n" +
                                      "It will be exported to `{GATEWAY_HOME}/data/security/keystores/` with a name of `gateway-client-trust.<type>`" +
                                      "Using the --type option you can specify which keystore type you need (default: PEM)\n" +
                                      "NOTE: The password for the JKS, JCEKS and PKCS12 types is `changeit`.\n" +
                                      "It can be changed using: `keytool -storepasswd -storetype <type> -keystore gateway-client-trust.<type>`";

    private GatewayConfig getGatewayConfig() {
      GatewayConfig result;
      Configuration conf = getConf();
      if (conf instanceof GatewayConfig) {
        result = (GatewayConfig) conf;
      } else {
        result = new GatewayConfigImpl();
      }
      return result;
    }

    @Override
    public void execute() throws Exception {
      KeystoreService ks = getKeystoreService();

      if (ks != null) {
        try {
          if (!ks.isKeystoreForGatewayAvailable()) {
            out.println("No keystore has been created for the gateway. Please use the create-cert command or populate with a CA signed cert of your own.");
          }

          GatewayConfig config = getGatewayConfig();
          Certificate cert = ks.getKeystoreForGateway().getCertificate(config.getIdentityKeyAlias());
          String keyStoreDir = config.getGatewayKeystoreDir() + File.separator;
          File ksd = new File(keyStoreDir);
          if (!ksd.exists()) {
            if (!ksd.mkdirs()) {
              // certainly should not happen if the keystore is known to be available
              throw new ServiceLifecycleException("Unable to create keystores directory" + ksd.getAbsolutePath());
            }
          }

          if ("PEM".equalsIgnoreCase(type) || type == null) {
            X509CertificateUtil.writeCertificateToFile(cert, new File(keyStoreDir + "gateway-client-trust.pem"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-client-trust.pem");
          } else if ("JKS".equalsIgnoreCase(type)) {
            X509CertificateUtil.writeCertificateToJks(cert, new File(keyStoreDir + "gateway-client-trust.jks"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-client-trust.jks");
          } else if ("JCEKS".equalsIgnoreCase(type)) {
            X509CertificateUtil.writeCertificateToJceks(cert, new File(keyStoreDir + "gateway-client-trust.jceks"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-client-trust.jceks");
          } else if ("PKCS12".equalsIgnoreCase(type)) {
            X509CertificateUtil.writeCertificateToPkcs12(cert, new File(keyStoreDir + "gateway-client-trust.pkcs12"));
            out.println("Certificate gateway-identity has been successfully exported to: " + keyStoreDir + "gateway-client-trust.pkcs12");
          } else {
            out.println("Invalid type for export file provided. Export has not been done. Please use: [PEM|JKS|JCEKS|PKCS12] default value is PEM.");
          }
        } catch (KeystoreServiceException e) {
          throw new ServiceLifecycleException("The identity keystore was not loaded properly - the provided password may not match the password for the keystore.", e);
        }
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

 public class CertCreateCommand extends Command {

  public static final String USAGE = "create-cert [--force] [--hostname h]";
  public static final String DESC = "The create-cert command populates the configured identity\n" +
                                    "keystore with a self-signed certificate to be used as the\n" +
                                    "gateway identity. If a cert exists and it is determined to\n" +
                                    "not have been generated by Knox, --force must be specified\n" +
                                    "to overwrite it.  If a self-signed cert is created, a\n" +
                                    "password for the key will be generated and stored in the\n" +
                                    "__gateway-credentials.jceks credential store.";
  private static final String GATEWAY_CREDENTIAL_STORE_NAME = "__gateway";

   public CertCreateCommand() {
   }

   @Override
   public void execute() throws Exception {
     KeystoreService ks = getKeystoreService();

     AliasService as = getAliasService();

     if (ks != null) {
       try {
         if (!ks.isCredentialStoreForClusterAvailable(GATEWAY_CREDENTIAL_STORE_NAME)) {
//           log.creatingCredentialStoreForGateway();
           ks.createCredentialStoreForCluster(GATEWAY_CREDENTIAL_STORE_NAME);
         } else {
//           log.credentialStoreForGatewayFoundNotCreating();
         }
         // LET'S NOT GENERATE A DIFFERENT KEY PASSPHRASE BY DEFAULT ANYMORE
         // IF A DEPLOYMENT WANTS TO CHANGE THE KEY PASSPHRASE TO MAKE IT MORE SECURE THEN
         // THEY CAN ADD THE ALIAS EXPLICITLY WITH THE CLI
         //as.generateAliasForCluster(GATEWAY_CREDENTIAL_STORE_NAME, GATEWAY_IDENTITY_PASSPHRASE);
       } catch (KeystoreServiceException e) {
         throw new ServiceLifecycleException("Keystore was not loaded properly - the stored password may not match the password for the keystore.", e);
       }

       try {
         if (!ks.isKeystoreForGatewayAvailable()) {
//           log.creatingKeyStoreForGateway();
           ks.createKeystoreForGateway();
         }
         else {
//           log.keyStoreForGatewayFoundNotCreating();
         }


         GatewayConfig config = getGatewayConfig();

         if ( !isForceRequired(config, ks) || force) {
           char[] passphrase = as.getGatewayIdentityPassphrase();
           if (passphrase == null) {
             MasterService ms = services.getService(ServiceType.MASTER_SERVICE);
             passphrase = ms.getMasterSecret();
           }
           ks.addSelfSignedCertForGateway(config.getIdentityKeyAlias(), passphrase, hostname);
//         logAndValidateCertificate();
           out.println("Certificate " + config.getIdentityKeyAlias() + " has been successfully created.");
         } else {
           // require --force to replace...
           out.println("A non-self-signed certificate has already been installed in the configured keystore. " +
               "Please use --force if you wish to overwrite it with a generated self-signed certificate.");
         }
       } catch (KeystoreServiceException e) {
         throw new ServiceLifecycleException("The identity keystore was not loaded properly - the provided password may not match the password for the keystore.", e);
       }
     }
   }

   /**
    * Determines if <code>--force</code> should be used inorder to not accidentally overwrite a
    * real certificate.
    * <p>
    * <p>
    * All of the following must be met for <code>--force</code> to <b>NOT</b> be required:
    * <ul>
    * <li>The path to the keystore file is the default path: <code>[Gateway Keystore Directory]/gateway.jks</code></li>
    * <li>The alias name for the key is the default name: <code>gateway-identity</code></li>
    * <li>The relevant certificate does not exist or is self-signed</li>
    * <li>The relevant certificate has a subject name ending in "OU=Test,O=Hadoop,L=Test,ST=Test,C=US"</li>
    * </ul>
    *
    * @param config the Gateway configuration
    * @param ks     a {@link KeystoreService} implementation
    * @return <code>true</code> if <code>--force</code> is required; otherwise <code>false</code>
    */
   private boolean isForceRequired(GatewayConfig config, KeystoreService ks) {

     // Test the path of the keystore file
     Path defaultKeystorePath = Paths.get(config.getGatewayKeystoreDir(), GatewayConfig.DEFAULT_GATEWAY_KEYSTORE_NAME).toAbsolutePath();
     Path actualKeystorePath = Paths.get(config.getIdentityKeystorePath()).toAbsolutePath();
     if (!defaultKeystorePath.equals(actualKeystorePath)) {
       // The path is not the default path: --force is required
       return true;
     }

     // Test the alias name for the key
     if (!GatewayConfig.DEFAULT_IDENTITY_KEY_ALIAS.equals(config.getIdentityKeyAlias())) {
       // The alias name for the key is not the default name (gateway-identity): --force is required
       return true;
     }

     // Test the certificate
     try {
       Certificate certificate = ks.getCertificateForGateway();

       if (certificate instanceof X509Certificate) {
         if (!X509CertificateUtil.isSelfSignedCertificate(certificate)) {
           // The relevant certificate exists and is not self-signed: --force is required
           return true;
         } else if (!((X509Certificate) certificate).getSubjectDN().getName().matches(".*?,\\s*OU=Test,\\s*O=Hadoop,\\s*L=Test,\\s*ST=Test,\\s*C=US")) {
           // The subject name of certificate does not end with "OU=Test,O=Hadoop,L=Test,ST=Test,C=US": --force is required
           return true;
         }
       }
     } catch (KeyStoreException | KeystoreServiceException e) {
       // A certificate was (probably) not previously created...
     }

     // All indicators point to a previously created test certificate: --force is not required
     return false;
   }

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

  private String name;

   public AliasCreateCommand(String alias) {
     name = alias;
   }

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
       if (Boolean.parseBoolean(generate)) {
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

   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }
 }

 public class AliasDeleteCommand extends Command {
  public static final String USAGE = "delete-alias aliasname [--cluster clustername]";
  public static final String DESC = "The delete-alias command removes the\n" +
                                    "indicated alias from the --cluster specific\n" +
                                    "credential store or the gateway credential store.";

  private String name;

   public AliasDeleteCommand(String alias) {
     name = alias;
   }

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

   @Override
   public String getUsage() {
     return USAGE + ":\n\n" + DESC;
   }

 }

  public class BatchAliasCreateCommand extends Command {
    public static final String USAGE = "create-aliases " +
            "--alias alias1 [--value value1] " +
            "--alias alias2 [--value value2] " +
            "--alias aliasN [--value valueN] ... " +
            "[--cluster clustername] " +
            "[--generate]";
    public static final String DESC = "The create-aliases command will create multiple aliases\n"
            + "and secret pairs within the same credential store for the\n"
            + "indicated --cluster otherwise within the gateway\n"
            + "credential store. The actual secret may be specified via\n"
            + "the --value option or --generate (will create a random secret\n"
            + "for you) or user will be prompt to provide password.";

    protected List<String> names = new ArrayList<>();
    protected List<String> values = new ArrayList<>();

    public void addName(String alias) {
      if (names.contains(alias)) {
        out.println("Duplicated alias " + alias);
        System.exit(1);
      }
      names.add(alias);
      values.add(null);
    }

    public void addValue(String value) {
      values.set(values.size() -1, value);
    }

    @Override
    public void execute() throws Exception {
      Map<String, String> aliases = toMap();
      List<String> generated = new ArrayList<>();
      AliasService as = getAliasService();
      if (cluster == null) {
        cluster = "__gateway";
      }
      fillMissingValues(aliases, generated);
      as.addAliasesForCluster(cluster, aliases);
      printResults(generated, aliases);
    }

    protected void printResults(List<String> generated, Map<String, String> aliases) {
      if (!generated.isEmpty()) {
        out.println(generated.size() + " alias(es) have been successfully generated: " + generated);
      }
      List<String> created = new ArrayList<>(aliases.keySet());
      created.removeAll(generated);
      if (!created.isEmpty()) {
        out.println(created.size() + " alias(es) have been successfully created: " + created);
      }
    }

    protected void fillMissingValues(Map<String, String> aliases, List<String> generated) {
      for (Map.Entry<String, String> entry : aliases.entrySet()) {
        if (entry.getValue() == null) {
          if (Boolean.parseBoolean(generate)) {
            entry.setValue(PasswordUtils.generatePassword(16));
            generated.add(entry.getKey());
          } else {
            entry.setValue(new String(promptUserForPassword()));
          }
        }
      }
    }

    private Map<String, String> toMap() {
      Map<String,String> aliases = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        aliases.put(names.get(i), values.get(i));
      }
      return aliases;
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

  public class CreateListAliasesCommand extends BatchAliasCreateCommand {
    public static final String USAGE = "create-list-aliases " +
            "--alias alias1 [--value value1] " +
            "--alias alias2 [--value value2] " +
            "--alias aliasN [--value valueN] ... " +
            "--cluster cluster1 " +
            "--alias aliasN [--value valueN] ..." +
            "--cluster clusterN " +
            "[--generate]";
    public static final String DESC = "The create-list-aliases command will create multiple aliases\n"
            + "and secret pairs within the same credential store for the\n"
            + "indicated --cluster(s) otherwise within the gateway\n"
            + "credential store. The actual secret may be specified via\n"
            + "the --value option or --generate (will create a random secret\n"
            + "for you) or user will be prompt to provide password.";

    private final Map<String, Map<String, String>> aliasMap = new LinkedHashMap<>();

    @Override
    public void execute() throws Exception {
      if (cluster == null || !names.isEmpty()) {
        cluster = "__gateway";
        this.toMap(cluster);
      }

      AliasService aliasService = getAliasService();

      for (Map.Entry<String, Map<String, String>> aliasesMapEntry : aliasMap.entrySet()) {
        List<String> generated = new ArrayList<>();
        this.fillMissingValues(aliasesMapEntry.getValue(), generated);
        aliasService.addAliasesForCluster(aliasesMapEntry.getKey(), aliasesMapEntry.getValue());
        this.printResults(generated, aliasesMapEntry.getValue());
        this.listAliasesForCluster(aliasesMapEntry.getKey(), aliasService);
      }
    }

    private void listAliasesForCluster(String cluster, AliasService as) throws AliasServiceException {
      out.println("Listing aliases for: " + cluster);
      List<String> aliases = as.getAliasesForCluster(cluster);
      for (String alias : aliases) {
        out.println(alias);
      }
      out.println("\n" + aliases.size() + " items.");
    }

    private void toMap(String cluster) {
      Map<String, String> parsedAliases = new LinkedHashMap<>();
      for (int i = 0; i < values.size(); i++) {
        parsedAliases.put(names.get(i), values.get(i));
      }

      names.clear();
      values.clear();
      aliasMap.put(cluster, parsedAliases);
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

  public static char[] promptUserForPassword() {
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

 public class MasterCreateCommand extends Command {
  public static final String USAGE = "create-master [--force] [--master mastersecret] [--generate]";
  public static final String DESC = "The create-master command persists the master secret in a file located at:\n" +
                                    "{GATEWAY_HOME}/data/security/master.\n" +
                                    "It will prompt the user for the secret to persist.\n" +
                                    "Use --force to overwrite the master secret.\n" +
                                    "Use --master to pass in a master secret to persist.\n" +
                                    "This can be used to persist the secret without any user interaction.\n" +
                                    "Be careful as the secret might appear in shell histories or process listings.\n" +
                                    "Instead of --master it is usually a better idea to use --generate instead!\n" +
                                    "Use --generate to have Knox automatically generate a random secret.\n" +
                                    "The generated secret will not be printed or otherwise exposed.\n" +
                                    "Do not specify both --master and --generate at the same time.\n";

   public MasterCreateCommand() {
   }

   private GatewayConfig getGatewayConfig() {
     GatewayConfig result;
     Configuration conf = getConf();
     if( conf instanceof GatewayConfig ) {
       result = (GatewayConfig)conf;
     } else {
       result = new GatewayConfigImpl();
     }
     return result;
   }

   @Override
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

   @Override
   public void execute() throws Exception {
     out.println("Master secret has been persisted to disk.");
   }

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
          ts.redeployTopology(cluster);
        }
        else {
          out.println("Invalid cluster name provided. Nothing to redeploy.");
        }
      }
    }

    /**
     * @param cluster Cluster name to validate against the TopologyService
     * @param ts ToplogyService to validate the given cluster name
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

    @Override
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
    protected String username;
    protected char[] password;
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
      public MissingUsernameException() {}

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
      ini.loadFromPath(config);
      return authenticateUser(ini, token);
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
      } catch (MissingUsernameException | NoSuchProviderException | MissingPasswordException | NullPointerException | AliasServiceException e) {
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
      String user;
      Provider shiroProvider = t.getProvider("authentication", "ShiroProvider");
      if(shiroProvider != null){
        Map<String, String> params = shiroProvider.getParams();
        user = params.get(SYSTEM_USERNAME);
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
    private char[] getSystemPassword(Topology t) throws NoSuchProviderException, MissingPasswordException, AliasServiceException {
      final String SYSTEM_PASSWORD = "main.ldapRealm.contextFactory.systemPassword";
      String pass;
      Provider shiro = t.getProvider("authentication", "ShiroProvider");
      if(shiro != null){
        Map<String, String> params = shiro.getParams();
        pass = params.get(SYSTEM_PASSWORD);
        if (pass.startsWith(ALIAS_PREFIX) && pass.endsWith("}")) {
          final String alias = pass.substring("${ALIAS=".length(), pass.length() - 1);
          out.println(String.format(Locale.getDefault(), "System password is stored as an alias %s; looking it up...", alias));
          pass = String.valueOf(getAliasService().getPasswordFromAliasForCluster(cluster, alias));
        }
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
        @SuppressWarnings("deprecation")
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
        } else {
          try(InputStreamReader inputStreamReader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
              BufferedReader reader = new BufferedReader(inputStreamReader)) {
            out.println("Username: ");
            this.username = reader.readLine();
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
          try(InputStreamReader inputStreamReader = new InputStreamReader(System.in, StandardCharsets.UTF_8);
              BufferedReader reader = new BufferedReader(inputStreamReader)) {
            out.println("Password: ");
            String pw = reader.readLine();
            if(pw != null){
              this.password = pw.toCharArray();
            } else {
              this.password = new char[0];
            }
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
    private Set<String> groupSet = new HashSet<>();

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

    private Set<String> getGroups(Topology t, UsernamePasswordToken token){
      Set<String> groups = null;
      try {
        Subject subject = getSubject(getConfig(t));
        if(!subject.isAuthenticated()) {
          subject.login(token);
        }
        subject.hasRole(""); //Populate subject groups
        groups = (Set) subject.getSession().getAttribute(SUBJECT_USER_GROUPS);
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
    if(conf instanceof GatewayConfig) {
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
    private int attempts;

    @Override
    public String getUsage() { return USAGE + ":\n\n" + DESC; }

    @Override
    public void execute() {
      attempts++;
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
          host = InetAddress.getLocalHost().getHostName();
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

//    Initialize the HTTP client
      client = HttpClients.createDefault();

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
        try(CloseableHttpResponse response = client.execute(request)) {

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
        }
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
    private File sourceFile;
    protected String filename;

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

    @Override
    public void execute() throws Exception {
      super.execute(getEntryName(PROVIDER_CONFIG_ENTRY), getSourceFile());
    }

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

    @Override
    public void execute() throws Exception {
      super.execute(getEntryName(DESCRIPTORS_ENTRY), getSourceFile());
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  public class RemoteRegistryGetACLCommand extends RemoteRegistryCommand {

    static final String USAGE = "get-registry-acl entry --registry-client name";
    static final String DESC = "Presents the ACL settings for the specified remote registry entry.\n";

    private String entry;

    RemoteRegistryGetACLCommand(String entry) {
      this.entry = entry;
    }

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

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }


  /**
   * Base class for remote config registry delete commands
   */
  public abstract class RemoteRegistryDeleteCommand extends RemoteRegistryCommand {
    protected String entryName;

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

  public class TopologyConverter extends Command {

    public static final String USAGE =
        "convert-topology --path \"path/to/topology.xml\" --provider-name my-provider.json [--descriptor-name my-descriptor.json] "
            + "[--topology-name topologyName] [--output-dir \"path/to/configs/\"] [--force] [--cluster clusterName] [--discovery-url url] "
            + "[--discovery-user discoveryUser] [--discovery-pwd-alias discoveryPasswordAlias] [--discovery-type discoveryType]";
    public static final String DESC =
        "Convert Knox topology file to provider and descriptor config files \n"
            + "Options are as follows: \n"
            + "--path (required) path to topology xml file \n"
            + "--provider-name (required) name of the provider json config file (including .json extension) \n"
            + "--descriptor-name (optional) name of descriptor json config file (including .json extension) \n"
            + "--topology-name (optional) topology-name can be use instead of --path option, if used, KnoxCLI will attempt to find topology from deployed topologies.\n"
            + "\t if not provided topology name will be used as descriptor name \n"
            + "--output-dir (optional) output directory to save provider and descriptor config files. Default is the current working directory. \n"
            + "\t if not provided config files will be saved in appropriate Knox config directory \n"
            + "--force (optional) force rewriting of existing files, if not used, command will fail when the configs files with same name already exist. \n"
            + "--cluster (optional) cluster name, required for service discovery \n"
            + "--discovery-url (optional) service discovery URL, required for service discovery \n"
            + "--discovery-user (optional) service discovery user, required for service discovery \n"
            + "--discovery-pwd-alias (optional) password alias for service discovery user, required for service discovery \n"
            + "--discovery-type (optional) service discovery type, required for service discovery \n";

    public TopologyConverter() {
      super();
    }

    @Override
    public void execute() throws Exception {
      if (StringUtils.isBlank(FilenameUtils.getExtension(providerName))
          || StringUtils.isBlank(FilenameUtils.getExtension(descriptorName))) {
        throw new IllegalArgumentException(
            " JSON extension is required for provider and descriptor file names");
      }

      final TopologyToDescriptor converter = new TopologyToDescriptor();

      converter.setForce(force);
      if (!StringUtils.isBlank(topologyName)) {
        converter.setTopologyPath(
            getGatewayConfig().getGatewayTopologyDir() + File.separator
                + topologyName);
      } else if (!StringUtils.isBlank(path)) {
        converter.setTopologyPath(path);
      } else {
        throw new IllegalArgumentException(
            "Please specify either --path or --topology-name option");
      }
      if (!StringUtils.isBlank(providerName)) {
        converter.setProviderName(providerName);
      }
      if (!StringUtils.isBlank(descriptorName)) {
        converter.setDescriptorName(descriptorName);
      }
      /* if output location is provided then use it */
      if (!StringUtils.isBlank(outputDir)) {
        converter.setProviderConfigDir(outputDir);
        converter.setDescriptorConfigDir(outputDir);
      } else {
        converter.setProviderConfigDir(
            getGatewayConfig().getGatewayProvidersConfigDir());
        converter.setDescriptorConfigDir(
            getGatewayConfig().getGatewayDescriptorsDir());
      }
      /* set discovery params */
      if (!StringUtils.isBlank(cluster)) {
        converter.setCluster(cluster);
      }
      if (!StringUtils.isBlank(discoveryUrl)) {
        converter.setDiscoveryUrl(discoveryUrl);
      }
      if (!StringUtils.isBlank(discoveryUser)) {
        converter.setDiscoveryUser(discoveryUser);
      }
      if (!StringUtils.isBlank(discoveryPasswordAlias)) {
        converter.setDiscoveryPasswordAlias(discoveryPasswordAlias);
      }
      if (!StringUtils.isBlank(discoveryType)) {
        converter.setDiscoveryType(discoveryType);
      }

      converter.validate();
      converter.convert();

      final String topoName = StringUtils.isBlank(topologyName) ?  FilenameUtils.getBaseName(path) : topologyName;
      out.println(
          "Provider " + providerName + " and descriptor " + descriptorName
              + " generated for topology " + topoName
              + "\n");
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  public class GenerateDescriptorCommand extends Command {

    public static final String USAGE =
            "generate-descriptor --service-urls-file \"path/to/urls.txt\" --service-name SERVICE_NAME \n" +
                    "--provider-name my-provider.json --descriptor-name my-descriptor.json \n" +
                    "[--output-dir /path/to/output_dir] \n" +
                    "[--param key1 value1] \n" +
                    "[--param key2 value2] \n" +
                    "[--force] \n";
    public static final String DESC =
            "Create Knox topology descriptor file for one service\n"
                    + "Options are as follows: \n"
                    + "--service-urls-file (required) path to a text file containing service urls \n"
                    + "--service-name (required) the name of the service, such as WEBHDFS, IMPALAUI or HIVE \n"
                    + "--descriptor-name (required) name of descriptor to be created \n"
                    + "--provider-name (required) name of the referenced shared provider \n"
                    + "--output-dir (optional) output directory to save the descriptor file \n"
                    + "--param (optional) service param name and value \n"
                    + "--force (optional) force rewriting of existing files, if not used, command will fail when the configs files with same name already exist. \n";

    @Override
    public void execute() throws Exception {
      validateParams();
      File output = StringUtils.isBlank(outputDir) ? new File(".") : new File(outputDir);
      DescriptorGenerator generator =
              new DescriptorGenerator(descriptorName, providerName, serviceName, ServiceUrls.fromFile(urlsFilePath), params);
      generator.saveDescriptor(output, force);
      out.println("Descriptor " + descriptorName + " was successfully saved to " + output.getAbsolutePath() + "\n");
    }

    private void validateParams() {
      if (StringUtils.isBlank(FilenameUtils.getExtension(providerName))
              || StringUtils.isBlank(FilenameUtils.getExtension(descriptorName))) {
        throw new IllegalArgumentException("JSON extension is required for provider and descriptor file names");
      }
      if (StringUtils.isBlank(urlsFilePath) ) {
        throw new IllegalArgumentException("Missing --service-urls-file");
      }
      if (!new File(urlsFilePath).isFile()) {
        throw new IllegalArgumentException(urlsFilePath + " does not exist");
      }
      if (StringUtils.isBlank(serviceName)) {
        throw new IllegalArgumentException("Missing --service-name");
      }
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

  }

  public class JWKGenerator extends Command {

    public static final String USAGE = "generate-jwk [--jwkAlg HS256|HS384|HS512] [--saveAlias alias] [--topology topology]";
    public static final String DESC =
        "Generates a JSON Web Key using the supplied algorithm name and prints the generated key value on the screen. \n"
            + "As an alternative to displaying this possibly sensitive information on the screen you may want to save it as an alias.\n"
            + "Options are as follows: \n"
            + "--jwkAlg (optional) defines the name of the desired JSON Web Signature algorithm name; defaults to HS256. Other accepted values are HS384 and HS512 \n"
            + "--saveAlias (optional) if this is set, the given alias name is used to save the generated JWK instead of printing it on the screen \n"
            + "--topology (optional) the name of the topology (aka. cluster) to be used when saving the JWK as an alias. If none specified, the alias is going to be saved for the Gateway \n";

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }

    @Override
    public void execute() throws Exception {
      final int keyLength = Integer.parseInt(jwsAlgorithm.getName().substring(2));
      try {
        final OctetSequenceKey jwk = new OctetSequenceKeyGenerator(keyLength).keyID(UUID.randomUUID().toString()).algorithm(jwsAlgorithm).generate();
        final String jwkAsText = jwk.getKeyValue().toJSONString().replace("\"", "");
        if (alias != null) {
          if (cluster == null) {
            cluster = "__gateway";
          }
          getAliasService().addAliasForCluster(cluster, alias, jwkAsText);
          out.println(alias + " has been successfully created.");
        } else {
          out.println(jwkAsText);
        }
      } catch (JOSEException e) {
        throw new RuntimeException("Error while generating " + keyLength + " bits JWK secret", e);
      }
    }
  }

  public class TokenMigration extends Command {

    static final String USAGE = "migrate-tokens [--progressCount num] [--verbose true|false] [--archivedMigrated true|false] [--migrateExpiredTokens true|false]";
    static final String DESC =
        "Migrates previously created Knox Tokens from the Gateway credential store into the configured JDBC TokenStateService backend.\n"
            + "Options are as follows: \n"
            + "--progressCount (optional) indicates the number of tokens after this tool displays progress on the standard output. Defaults to 10.\n"
            + "--archiveMigrated (optional) a boolean flag indicating if migrated tokens should not be removed completely. "
            + "Instead, tokens are going to be archived in a separate keystore called __tokens-credentials.jceks. Defaults to false\n"
            + "--verbose (optional) a boolean flag that controls of a more verbose output on the STDOUT when processing tokens. Defaults to false.\n"
            + "--migrateExpiredTokens (optional) a boolean flag indicating whether already expired tokens should be migrated into the configure TSS backend. Defaults to false";

    @Override
    public void execute() throws Exception {
      final TokenStateService tokenStateService = services.getService(ServiceType.TOKEN_STATE_SERVICE);
      if (isTokenMigrationTarget(tokenStateService)) {
        out.println("Migrating tokens from __gateway credential store into the configured TokenStateService backend...");
        final TokenMigrationTool tokenMigrationTool = new TokenMigrationTool(getAliasService(), tokenStateService, out);
        tokenMigrationTool.setArchiveMigratedTokens(archiveMigratedTokens);
        tokenMigrationTool.setMigrateExpiredTokens(migrateExpiredTokens);
        tokenMigrationTool.setProgressCount(progressCount);
        tokenMigrationTool.setVerbose(verbose);
        tokenMigrationTool.migrateTokensFromGatewayCredentialStore();
      } else {
        out.println("This tool is meant to migrate tokens into a JDBC TokenStateService backend. However, the currently configured one ("
            + tokenStateService.getClass().getCanonicalName() + ") does not fulfill this requirement!");
      }
    }

    private boolean isTokenMigrationTarget(TokenStateService tokenStateService) {
      return tokenStateService instanceof TokenMigrationTarget;
    }

    @Override
    public String getUsage() {
      return USAGE + ":\n\n" + DESC;
    }
  }

  private static Properties loadBuildProperties() {
    Properties properties = new Properties();
    String BUILD_PROPERTY = "build.properties";
    PrintStream out = System.out;
    try(InputStream inputStream = KnoxCLI.class.getClassLoader().getResourceAsStream( BUILD_PROPERTY )) {
      if (inputStream != null) {
        properties.load(inputStream);
      } else {
        out.println("Failed to find configuration file " + BUILD_PROPERTY);
      }
    } catch( IOException e ) {
      out.println("Failed to find configuration file " + BUILD_PROPERTY + e.getMessage());
    }
    return properties;
  }

  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new GatewayConfigImpl(), new KnoxCLI(), args);
    System.exit(res);
  }

  private static void configureKerberosSecurity( GatewayConfig config ) {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "true");
    System.setProperty(GatewayConfig.KRB5_CONFIG, config.getKerberosConfig());
    System.setProperty(GatewayConfig.KRB5_DEBUG, Boolean.toString(config.isKerberosDebugEnabled()));
    System.setProperty(GatewayConfig.KRB5_LOGIN_CONFIG, config.getKerberosLoginConfig());
    System.setProperty(GatewayConfig.KRB5_USE_SUBJECT_CREDS_ONLY,  "false");
  }
}
