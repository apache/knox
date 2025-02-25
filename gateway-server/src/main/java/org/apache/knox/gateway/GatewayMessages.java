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
package org.apache.knox.gateway;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.apache.commons.cli.ParseException;
import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;
import org.apache.knox.gateway.services.security.KeystoreServiceException;
import org.apache.knox.gateway.topology.monitor.db.LocalDirectory;

@Messages(logger="org.apache.knox.gateway")
public interface GatewayMessages {

  @Message( level = MessageLevel.FATAL, text = "Failed to parse command line: {0}" )
  void failedToParseCommandLine( @StackTrace( level = MessageLevel.FATAL ) ParseException e );

  @Message( level = MessageLevel.INFO, text = "Starting gateway..." )
  void startingGateway();

  @Message( level = MessageLevel.FATAL, text = "Failed to start gateway: {0}" )
  void failedToStartGateway( @StackTrace( level = MessageLevel.FATAL ) Exception e );

  @Message( level = MessageLevel.INFO, text = "Started gateway on port {0}." )
  void startedGateway( String port );

  @Message( level = MessageLevel.INFO, text = "Stopping gateway..." )
  void stoppingGateway();

  @Message( level = MessageLevel.INFO, text = "Stopped gateway." )
  void stoppedGateway();

  @Message( level = MessageLevel.INFO, text = "Failed to stopped gateway." )
  void failedToStopGateway(@StackTrace( level = MessageLevel.INFO ) Exception e);

  @Message( level = MessageLevel.INFO, text = "Loading configuration resource {0}" )
  void loadingConfigurationResource( String res );

  @Message( level = MessageLevel.INFO, text = "Loading configuration file {0}" )
  void loadingConfigurationFile( String file );

  @Message( level = MessageLevel.INFO, text = "Failed to find configuration file {0}" )
  void failedToFindConfig( String path );

  @Message( level = MessageLevel.INFO, text = "Failed to find configuration file {0}: {1}" )
  void failedToFindConfig( String path, @StackTrace( level = MessageLevel.INFO ) Exception e );

  @Message( level = MessageLevel.WARN, text = "Failed to load configuration file {0}: {1}" )
  void failedToLoadConfig( String path, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.INFO, text = "Using {1} as GATEWAY_HOME via {0}." )
  void settingGatewayHomeDir( String location, String home );

  @Message( level = MessageLevel.INFO, text = "Loading topologies from directory: {0}" )
  void loadingTopologiesFromDirectory( String topologiesDir );

  @Message( level = MessageLevel.INFO, text = "Loading descriptors from directory: {0}" )
  void loadingDescriptorsFromDirectory(String descriptorsDir);

  @Message( level = MessageLevel.DEBUG, text = "Loading topology file: {0}" )
  void loadingTopologyFile( String fileName );

  @Message( level = MessageLevel.INFO, text = "Configured monitoring topologies in directory: {0}" )
  void configuredMonitoringTopologyChangesInDirectory( String topologiesDir );

  @Message( level = MessageLevel.INFO, text = "Deploying topology {0} to {1}" )
  void deployingTopology( String clusterName, String warDirName );

  @Message( level = MessageLevel.DEBUG, text = "Deployed topology {0}." )
  void deployedTopology( String clusterName );

  @Message( level = MessageLevel.INFO, text = "Loading topology {0} from {1}" )
  void redeployingTopology( String clusterName, String warDirName );

  @Message( level = MessageLevel.DEBUG, text = "Redeployed topology {0}." )
  void redeployedTopology( String clusterName );

  @Message( level = MessageLevel.INFO, text = "Activating topology {0}" )
  void activatingTopology( String name );

  @Message( level = MessageLevel.INFO, text = "Activating topology {0} archive {1}" )
  void activatingTopologyArchive( String topology, String archive );

  @Message( level = MessageLevel.INFO, text = "Deactivating topology {0}" )
  void deactivatingTopology( String name );

  @Message( level = MessageLevel.ERROR, text = "Failed to deploy topology {0}: {1}" )
  void failedToDeployTopology( String name, @StackTrace(level=MessageLevel.DEBUG) Throwable e );

  @Message( level = MessageLevel.ERROR, text = "Failed to redeploy topology {0}" )
  void failedToRedeployTopology( String name );

  @Message( level = MessageLevel.ERROR, text = "Failed to redeploy topology {0}: {1}" )
  void failedToRedeployTopology( String name, @StackTrace(level=MessageLevel.DEBUG) Throwable e );

  @Message(level = MessageLevel.ERROR, text = "Failed to load topology {0}: Topology configuration is invalid!")
  void failedToLoadTopology(String fileName);

  @Message( level = MessageLevel.ERROR, text = "Failed to redeploy topologies: {0}" )
  void failedToRedeployTopologies( @StackTrace(level=MessageLevel.DEBUG) Throwable e );

  @Message( level = MessageLevel.ERROR, text = "Failed to undeploy topology {0}: {1}" )
  void failedToUndeployTopology( String name, @StackTrace(level=MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.WARN, text = "Failed to validate topology {0} error {1}. Please "
      + "consider using descriptors instead of topologies" )
  void failedToValidateTopology( String name, String cause );

  @Message( level = MessageLevel.INFO, text = "Deleting topology {0}" )
  void deletingTopology( String topologyName );

  @Message( level = MessageLevel.INFO, text = "Deleting deployed topology {0}" )
  void deletingDeployment( String warDirName );

  @Message( level = MessageLevel.DEBUG, text = "Purge backups of deployed topology {0}" )
  void cleanupDeployments( String topologyName );

  @Message( level = MessageLevel.INFO, text = "Deleting backup deployed topology {0}" )
  void cleanupDeployment( String absolutePath );

  @Message( level = MessageLevel.INFO, text = "Creating gateway home directory: {0}" )
  void creatingGatewayHomeDir( File homeDir );

  @Message( level = MessageLevel.INFO, text = "Creating gateway deployment directory: {0}" )
  void creatingGatewayDeploymentDir( File topologiesDir );

  @Message( level = MessageLevel.INFO, text = "Creating default gateway configuration file: {0}" )
  void creatingDefaultConfigFile( File defaultConfigFile );

  @Message( level = MessageLevel.INFO, text = "Creating sample topology file: {0}" )
  void creatingDefaultTopologyFile( File defaultConfigFile );

  @Message( level = MessageLevel.WARN, text = "Ignoring service deployment contributor with invalid null name: {0}" )
  void ignoringServiceContributorWithMissingName( String className );

  @Message( level = MessageLevel.WARN, text = "Ignoring service deployment contributor with invalid null role: {0}" )
  void ignoringServiceContributorWithMissingRole( String className );

  @Message( level = MessageLevel.WARN, text = "Ignoring service deployment contributor with invalid null version: {0}" )
  void ignoringServiceContributorWithMissingVersion( String className );

  @Message( level = MessageLevel.WARN, text = "Ignoring provider deployment contributor with invalid null name: {0}" )
  void ignoringProviderContributorWithMissingName( String className );

  @Message( level = MessageLevel.WARN, text = "Ignoring provider deployment contributor with invalid null role: {0}" )
  void ignoringProviderContributorWithMissingRole( String className );

  @Message( level = MessageLevel.INFO, text = "Loaded logging configuration: {0}" )
  void loadedLoggingConfig( String fileName );

  @Message( level = MessageLevel.WARN, text = "Failed to load logging configuration: {0}" )
  void failedToLoadLoggingConfig( String fileName );

  @Message( level = MessageLevel.INFO, text = "Creating credential store for the gateway instance." )
  void creatingCredentialStoreForGateway();

  @Message( level = MessageLevel.INFO, text = "Credential store for the gateway instance found - no need to create one." )
  void credentialStoreForGatewayFoundNotCreating();

  @Message( level = MessageLevel.INFO, text = "Creating keystore for the gateway instance." )
  void creatingKeyStoreForGateway();

  @Message( level = MessageLevel.ERROR, text = "Unable to obtain the password for the keystore for the gateway instance: {0}" )
  void failedToGetPasswordForGatewayIdentityKeystore(Exception e);

  @Message( level = MessageLevel.ERROR, text = "Unable to obtain the password for the gateway identity key: {0}" )
  void failedToGetPassphraseForGatewayIdentityKey(Exception e);

  @Message( level = MessageLevel.INFO, text = "Keystore for the gateway instance found - no need to create one." )
  void keyStoreForGatewayFoundNotCreating();

  @Message( level = MessageLevel.INFO, text = "Creating credential store for the cluster: {0}" )
  void creatingCredentialStoreForCluster(String clusterName);

  @Message( level = MessageLevel.INFO, text = "Credential store found for the cluster: {0} - no need to create one." )
  void credentialStoreForClusterFoundNotCreating(String clusterName);

  @Message(level = MessageLevel.WARN, text = "An existing credential store found for the cluster {0} with a different type of {1}")
  void credentialStoreForClusterFoundWithDifferentType(String clusterName, String existingCredentialStoreType);

  @Message( level = MessageLevel.ERROR, text = "Unable to obtain the password for the gateway truststore using the alias {0}: {1}" )
  void failedToGetPasswordForGatewayTruststore(String alias, Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Received request: {0} {1}" )
  void receivedRequest( String method, String uri );

  @Message( level = MessageLevel.DEBUG, text = "Dispatch request: {0} {1}" )
  void dispatchRequest( String method, URI uri );

  @Message( level = MessageLevel.WARN, text = "Connection exception dispatching request: {0} {1}" )
  void dispatchServiceConnectionException( URI uri, @StackTrace(level=MessageLevel.WARN) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Signature verified: {0}" )
  void signatureVerified( boolean verified );

  @Message( level = MessageLevel.DEBUG, text = "Apache Knox Gateway {0} ({1})" )
  void gatewayVersionMessage( String version, String hash );

  @Message( level = MessageLevel.ERROR, text = "Failed to inject service {0}: {1}" )
  void failedToInjectService( String serviceName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to finalize contribution: {0}" )
  void failedToFinalizeContribution( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to contribute service [role={1}, name={0}]: {2}" )
  void failedToContributeService( String name, String role, @StackTrace( level = MessageLevel.ERROR ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to contribute provider [role={1}, name={0}]: {2}" )
  void failedToContributeProvider( String name, String role, @StackTrace( level = MessageLevel.ERROR ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize contribution: {0}" )
  void failedToInitializeContribution( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize servlet instance: {0}" )
  void failedToInitializeServletInstace( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Gateway processing failed: {0}" )
  void failedToExecuteFilter( @StackTrace( level = MessageLevel.INFO ) Throwable t );

  @Message( level = MessageLevel.ERROR, text = "Failed to load topology {0}: {1}")
  void failedToLoadTopology( String fileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to load topology {0}, retrying after {1}ms: {2}")
  void failedToLoadTopologyRetrying( String friendlyURI, String delay, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to handle topology events: {0}" )
  void failedToHandleTopologyEvents( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to reload topologies: {0}" )
  void failedToReloadTopologies( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.FATAL, text = "Unsupported encoding: {0}" )
  void unsupportedEncoding( @StackTrace( level = MessageLevel.FATAL ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to persist master secret: {0}" )
  void failedToPersistMasterSecret( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to encrypt master secret: {0}" )
  void failedToEncryptMasterSecret( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize master service from persistent master {0}: {1}" )
  void failedToInitializeFromPersistentMaster( String masterFileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to encode passphrase: {0}" )
  void failedToEncodePassphrase( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to verify signature: {0}")
  void failedToVerifySignature( @StackTrace(level=MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to sign the data: {0}")
  void failedToSignData( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to decrypt password for cluster {0}: {1}" )
  void failedToDecryptPasswordForCluster( String clusterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to encrypt password for cluster {0}: {1}")
  void failedToEncryptPasswordForCluster( String clusterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to create keystore [filename={0}, type={1}]: {2}" )
  void failedToCreateKeystore( String fileName, String keyStoreType, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to load keystore [filename={0}, type={1}]: {2}" )
  void failedToLoadKeystore( String fileName, String keyStoreType, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to add key for cluster {0}: {1}" )
  void failedToAddKeyForCluster( String clusterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to add credential for cluster {0}: {1}" )
  void failedToAddCredentialForCluster( String clusterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to add credentials for cluster {0}: {1}" )
  void failedToAddCredentialsForCluster( String clusterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to get key for Gateway {0}: {1}" )
  void failedToGetKeyForGateway( String alias, @StackTrace( level=MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to get credential for cluster {0}: {1}" )
  void failedToGetCredentialForCluster( String clusterName, @StackTrace(level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to get key for cluster {0}: {1}" )
  void failedToGetKeyForCluster( String clusterName, @StackTrace(level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to add self signed certificate for Gateway {0}: {1}" )
  void failedToAddSeflSignedCertForGateway( String alias, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to generate secret key from password: {0}" )
  void failedToGenerateKeyFromPassword( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to establish connection to {0}: {1}" )
  void failedToEstablishConnectionToUrl( String url, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to interpret property \"{0}\": {1}")
  void failedToInterpretProperty( String property, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to instantiate the internal gateway services." )
  void failedToInstantiateGatewayServices();

  @Message( level = MessageLevel.ERROR, text = "Failed to serialize map to Json string {0}: {1}" )
  void failedToSerializeMapToJSON( Map<String, Object> map, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to get map from Json string {0}: {1}" )
  void failedToGetMapFromJsonString( String json, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Successful Knox->Hadoop SPNegotiation authentication for URL: {0}" )
  void successfulSPNegoAuthn(String uri);

  @Message( level = MessageLevel.ERROR, text = "Failed Knox->Hadoop SPNegotiation authentication for URL: {0}" )
  void failedSPNegoAuthn(String uri);

  @Message( level = MessageLevel.DEBUG, text = "Dispatch response status: {0}" )
  void dispatchResponseStatusCode(int statusCode);

  @Message( level = MessageLevel.DEBUG, text = "Dispatch response status: {0}, Location: {1}" )
  void dispatchResponseCreatedStatusCode( int statusCode, String location );

  @Message( level = MessageLevel.ERROR, text = "Failed to decrypt cipher text for cluster {0}: due to inability to retrieve the password." )
  void failedToDecryptCipherForClusterNullPassword(String clusterName);

  @Message( level = MessageLevel.DEBUG, text = "Gateway services have not been initialized." )
  void gatewayServicesNotInitialized();

  @Message( level = MessageLevel.INFO, text = "The Gateway SSL certificate is issued to hostname: {0}." )
  void certificateHostNameForGateway(String cn);

  @Message( level = MessageLevel.INFO, text = "The Gateway SSL certificate is valid between: {0} and {1}." )
  void certificateValidityPeriod(Date notBefore, Date notAfter);

  @Message( level = MessageLevel.ERROR, text = "Unable to retrieve certificate for Gateway: {0}." )
  void unableToRetrieveCertificateForGateway(Exception e);

  @Message( level = MessageLevel.ERROR, text = "Failed to generate alias for cluster: {0} {1}." )
  void failedToGenerateAliasForCluster(String clusterName, KeystoreServiceException e);

  @Message( level = MessageLevel.DEBUG, text = "Key passphrase not found in credential store - using master secret." )
  void assumingKeyPassphraseIsMaster();

  @Message( level = MessageLevel.ERROR, text = "Failed to remove alias for cluster: {0} {1}." )
  void failedToRemoveCredentialForCluster(String clusterName, Exception e);

  @Message( level = MessageLevel.WARN, text = "Failed to match path {0}" )
  void failedToMatchPath( String path );

  @Message( level = MessageLevel.ERROR, text = "Failed to get system ldap connection: {0}" )
  void failedToGetSystemLdapConnection( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.WARN, text = "Value not found for cluster:{0}, alias: {1}" )
  void aliasValueNotFound( String cluster, String alias );

  @Message( level = MessageLevel.INFO, text = "Computed userDn: {0} using dnTemplate for principal: {1}" )
  void computedUserDn(String userDn, String principal);

  @Message( level = MessageLevel.DEBUG, text = "Searching from {0} where {1} scope {2}" )
  void searchBaseFilterScope( String searchBase, String searchFilter, String searchScope );

  @Message( level = MessageLevel.INFO, text = "Computed userDn: {0} using ldapSearch for principal: {1}" )
  void searchedAndFoundUserDn(String userDn, String principal);

  @Message( level = MessageLevel.INFO, text = "Computed roles/groups: {0} for principal: {1}" )
  void lookedUpUserRoles(Set<String> roleNames, String userName);

  @Message( level = MessageLevel.DEBUG, text = "Initialize provider: {1}/{0}" )
  void initializeProvider( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Initialize service: {1}/{0}" )
  void initializeService( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Contribute provider: {1}/{0}" )
  void contributeProvider( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Contribute service: {1}/{0}" )
  void contributeService( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Finalize provider: {1}/{0}" )
  void finalizeProvider( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Finalize service: {1}/{0}" )
  void finalizeService( String name, String role );

  @Message( level = MessageLevel.DEBUG, text = "Configured services directory is {0}" )
  void usingServicesDirectory(String path);

  @Message( level = MessageLevel.ERROR, text = "Failed to unmarshall service definition file {0} file : {1}" )
  void failedToLoadServiceDefinition(String fileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Failed to find service definition file {0} file : {1}" )
  void failedToFindServiceDefinitionFile(String fileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Failed to find rewrite file {0} file : {1}" )
  void failedToFindRewriteFile(String fileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Failed to unmarshall rewrite file {0} file : {1}" )
  void failedToLoadRewriteFile(String fileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "No rewrite file found in service directory {0}" )
  void noRewriteFileFound(String path);

  @Message( level = MessageLevel.DEBUG, text = "Added Service definition name: {0}, role : {1}, version : {2}" )
  void addedServiceDefinition(String serviceName, String serviceRole, String version);

  @Message( level = MessageLevel.INFO, text = "System Property: {0}={1}" )
  void logSysProp( String name, String property );

  @Message( level = MessageLevel.ERROR, text = "Unable to get password: {0}" )
  void unableToGetPassword(@StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Initialize application: {0}" )
  void initializeApplication( String name );

  @Message( level = MessageLevel.DEBUG, text = "Contribute application: {0}" )
  void contributeApplication( String name );

  @Message( level = MessageLevel.DEBUG, text = "Finalize application: {0}" )
  void finalizeApplication( String name );

  @Message( level = MessageLevel.INFO, text = "Default topology {0} at {1}" )
  void defaultTopologySetup( String defaultTopologyName, String redirectContext );

  @Message( level = MessageLevel.DEBUG, text = "Default topology forward from {0} to {1}" )
  void defaultTopologyForward( String oldTarget, String newTarget );

  @Message( level = MessageLevel.ERROR, text = "Unable to setup PagedResults" )
  void unableToSetupPagedResults();

  @Message( level = MessageLevel.INFO, text = "Ignoring PartialResultException" )
  void ignoringPartialResultException();

  @Message( level = MessageLevel.WARN, text = "Only retrieved first {0} groups due to SizeLimitExceededException." )
  void sizeLimitExceededOnlyRetrieved(int numResults);

  @Message( level = MessageLevel.DEBUG, text = "Failed to parse path into Template: {0} : {1}" )
  void failedToParsePath( String path, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Failed to initialize metrics reporter {0}  : {1}" )
  void failedToInitializeReporter( String name,  @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Failed to start metrics reporter {0}  : {1}" )
  void failedToStartReporter( String name,  @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Failed to stop metrics reporter {0}  : {1}" )
  void failedToStopReporter( String name,  @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.INFO, text = "Cookie scoping feature enabled: {0}" )
  void cookieScopingFeatureEnabled( boolean enabled );

  @Message(level = MessageLevel.INFO,
           text = "Topology port mapping feature enabled: {0}")
  void gatewayTopologyPortMappingEnabled(boolean enabled);

  @Message(level = MessageLevel.ERROR,
           text = "No topology mapped to port: {0}")
  void noTopologyMappedToPort(String port);

  @Message(level = MessageLevel.ERROR,
           text = "Could not find topology {0} specified in port mapping config")
  void noMappedTopologyFound(String topology);

  @Message(level = MessageLevel.DEBUG,
           text = "Creating a connector for topology {0} listening on port {1}.")
  void createJettyConnector(String topology, String port);

  @Message(level = MessageLevel.DEBUG,
           text = "Creating a handler for topology {0}.")
  void createJettyHandler(String topology);

  @Message(level = MessageLevel.INFO,
           text = "Updating request context from {0} to {1}")
  void topologyPortMappingAddContext(String oldTarget,
      String newTarget);

  @Message(level = MessageLevel.DEBUG,
           text = "Updating request target from {0} to {1}")
  void topologyPortMappingUpdateRequest(String oldTarget,
      String newTarget);

  @Message(level = MessageLevel.ERROR,
           text = "Port {0} configured for Topology - {1} is already in use.")
  void portAlreadyInUse(String port, String topology);

  @Message(level = MessageLevel.ERROR,
           text = "Port {0} is already in use.")
  void portAlreadyInUse(String port);

  @Message(level = MessageLevel.INFO,
           text = "Started gateway, topology \"{0}\" listening on port \"{1}\".")
  void startedGateway(String topology, String port);

  @Message(level = MessageLevel.ERROR,
           text = "Topology \"{0}\" failed to start listening on port \"{1}\".")
  void startedGatewayPortConflict(String topology, String port);

  @Message(level = MessageLevel.ERROR,
           text =
               " Could not find topology \"{0}\" mapped to port \"{1}\" configured in gateway-config.xml. "
                   + "This invalid topology mapping will be ignored by the gateway. "
                   + "Gateway restart will be required if in the future \"{0}\" topology is added.")
  void topologyPortMappingCannotFindTopology(String topology, String port);

  @Message(level = MessageLevel.ERROR,
           text = "Port mapped topology {0} cannot be configured as default topology")
  void defaultTopologyInPortmappedTopology(String topology);


  @Message( level = MessageLevel.WARN, text = "There is no registry client defined for remote configuration monitoring." )
  void missingClientConfigurationForRemoteMonitoring();

  @Message( level = MessageLevel.WARN, text = "Could not resolve a remote configuration registry client for {0}." )
  void unresolvedClientConfigurationForRemoteMonitoring(String clientName);

  @Message( level = MessageLevel.INFO, text = "Configured monitoring simple descriptors in directory: {0}" )
  void configuredMonitoringDescriptorChangesInDirectory(String descriptorsDir);

  @Message( level = MessageLevel.INFO, text = "Configured monitoring shared provider configurations in directory: {0}" )
  void configuredMonitoringProviderConfigChangesInDirectory(String sharedProviderDir);

  @Message(level = MessageLevel.INFO, text = "Started monitoring {0}")
  void startedMonitor(String monitorName);

  @Message(level = MessageLevel.INFO, text = "Stopped monitoring {0}")
  void stoppedMonitor(String monitorName);

  @Message( level = MessageLevel.ERROR, text = "Error registering listener for remote configuration path {0} : {1}" )
  void errorAddingRemoteConfigurationListenerForPath(String path,
                                                     @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Error unregistering listener for remote configuration path {0} : {1}" )
  void errorRemovingRemoteConfigurationListenerForPath(String path,
                                                       @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.ERROR, text = "Error downloading remote configuration {0} : {1}" )
  void errorDownloadingRemoteConfiguration(String path,
                                           @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.INFO, text = "Prevented deletion of shared provider configuration because there are referencing descriptors: {0}" )
  void preventedDeletionOfSharedProviderConfiguration(String providerConfigurationPath);

  @Message( level = MessageLevel.INFO, text = "Empty result returned by SimpleDescriptorHandler for decriptor {0}" )
  void emptyHandleResult(String descriptorName);

  @Message( level = MessageLevel.INFO, text = "Generated topology {0} because the associated descriptor {1} changed." )
  void generatedTopologyForDescriptorChange(String topologyName, String descriptorName);

  @Message( level = MessageLevel.WARN, text = "An error occurred while attempting to initialize the remote configuration monitor: {0}" )
  void remoteConfigurationMonitorInitFailure(String errorMessage,
                                             @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.WARN, text = "An error occurred while attempting to start the remote configuration monitor {0} : {1}" )
  void remoteConfigurationMonitorStartFailure(String monitorType, String errorMessage);

  @Message( level = MessageLevel.INFO, text = "Starting remote configuration monitor for source {0} ..." )
  void startingRemoteConfigurationMonitor(String address);

  @Message( level = MessageLevel.INFO, text = "Monitoring remote configuration source {0}" )
  void monitoringRemoteConfigurationSource(String address);

  @Message( level = MessageLevel.INFO, text = "Remote configuration monitor downloaded {0} configuration file {1}" )
  void downloadedRemoteConfigFile(String type, String configFileName);

  @Message( level = MessageLevel.INFO, text = "Remote configuration monitor deleted {0} configuration file {1} based on remote change." )
  void deletedRemoteConfigFile(String type, String configFileName);

  @Message( level = MessageLevel.ERROR, text = "Failed to delete remote {0} file {1}." )
  void failedToDeletedRemoteConfigFile(String type, String configFileName);

  @Message( level = MessageLevel.ERROR, text = "An error occurred while processing {0} : {1}" )
  void simpleDescriptorHandlingError(String simpleDesc,
                                     @StackTrace(level = MessageLevel.INFO) Exception e);

  @Message(level = MessageLevel.DEBUG, text = "Successfully wrote configuration: {0}")
  void wroteConfigurationFile(String filePath);

  @Message(level = MessageLevel.ERROR, text = "Failed to write configuration: {0}")
  void failedToWriteConfigurationFile(String filePath,
                                      @StackTrace(level = MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.INFO, text = "Deleting topology {0} because the associated descriptor {1} was deleted." )
  void deletingTopologyForDescriptorDeletion(String topologyName, String descriptorName);

  @Message( level = MessageLevel.INFO, text = "Deleting descriptor {0} because the associated topology {1} was deleted." )
  void deletingDescriptorForTopologyDeletion(String descriptorName, String topologyName);

  @Message( level = MessageLevel.DEBUG, text = "Added descriptor {0} reference to provider configuration {1}." )
  void addedProviderConfigurationReference(String descriptorName, String providerConfigurationName);

  @Message( level = MessageLevel.DEBUG, text = "Removed descriptor {0} reference to provider configuration {1}." )
  void removedProviderConfigurationReference(String descriptorName, String providerConfigurationName);

  @Message( level = MessageLevel.DEBUG,
            text = "The permissions for the remote configuration registry entry \"{0}\" are such that its content may not be trustworthy." )
  void suspectWritableRemoteConfigurationEntry(String entryPath);

  @Message( level = MessageLevel.DEBUG,
            text = "Correcting the suspect permissions for the remote configuration registry entry \"{0}\"." )
  void correctingSuspectWritableRemoteConfigurationEntry(String entryPath);

  @Message(level = MessageLevel.INFO,
           text = "A cluster configuration change was noticed for {1} @ {0}")
  void noticedClusterConfigurationChange(String source, String clusterName);


  @Message(level = MessageLevel.INFO,
           text = "Triggering topology regeneration for descriptor {2} because of change to the {1} @ {0} configuration.")
  void triggeringTopologyRegeneration(String source, String clusterName, String affected);


  @Message(level = MessageLevel.ERROR,
           text = "Encountered an error processing {2} in response to a {1} @ {0} configuration change: {3}")
  void errorRespondingToConfigChange(String source,
                                     String clusterName,
                                     String descriptor,
                                     @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.INFO,
           text = "Adding alias {1} for cluster {0} locally (local keystore) ")
  void addAliasLocally(String cluster, String alias);

  @Message(level = MessageLevel.ERROR,
           text = "Error adding alias {1} for cluster {0} locally (local keystore), cause: {2} ")
  void errorAddingAliasLocally(String cluster, String alias, String cause);

  @Message(level = MessageLevel.ERROR, text = "Error adding remote alias entry listener for cluster {0} and alias {1}, cause: {2} ")
  void errorAddingRemoteAliasEntryListener(String cluster, String alias, String cause);

  @Message(level = MessageLevel.INFO,
           text = "Remove alias {1} for cluster {0} locally (local keystore) ")
  void removeAliasLocally(String cluster, String alias);

  @Message(level = MessageLevel.ERROR,
           text = "Error removing alias {1} for cluster {0} locally (local keystore), cause: {2} ")
  void errorRemovingAliasLocally(String cluster, String alias, String cause);

  @Message(level = MessageLevel.INFO,
           text = "Adding remote listener for path {0} ")
  void addRemoteListener(String path);

  @Message(level = MessageLevel.ERROR,
           text = "Error adding remote listener for path {0}, cause: {1} ")
  void errorAddingRemoteListener(String path, String cause);

  @Message(level = MessageLevel.ERROR,
           text = "Error removing remote listener for path {0}, cause: {1} ")
  void errorRemovingRemoteListener(String path, String cause);

  @Message(level = MessageLevel.INFO,
           text = "Remote Alias Service disabled")
  void remoteAliasServiceDisabled();

  @Message(level = MessageLevel.INFO,
           text = "Remote Alias Service enabled")
  void remoteAliasServiceEnabled();

  @Message( level = MessageLevel.ERROR, text = "The path to the keystore file does not exist: {0}" )
  void keystoreFileDoesNotExist(String path);

  @Message( level = MessageLevel.ERROR, text = "The path to the keystore file is not a file: {0}" )
  void keystoreFileIsNotAFile(String path);

  @Message( level = MessageLevel.ERROR, text = "The path to the keystore is not accessible: {0}" )
  void keystoreFileIsNotAccessible(String path);

  @Message(level = MessageLevel.INFO, text = "Starting service: {0}")
  void startingService(String serviceTypeName);

  @Message(level = MessageLevel.INFO, text = "Stopping service: {0}")
  void stoppingService(String serviceTypeName);

  @Message(level = MessageLevel.INFO, text = "Redeploying topology {0} due to service definition change {1} / {2} / {3}")
  void redeployingTopologyOnServiceDefinitionChange(String topologyName, String serviceName, String role, String version);

  @Message(level = MessageLevel.INFO, text = "Saved service definition {0} / {1} / {2}")
  void savedServiceDefinitionChange(String serviceName, String role, String version);

  @Message(level = MessageLevel.INFO, text = "Updated service definition {0} / {1} / {2}")
  void updatedServiceDefinitionChange(String serviceName, String role, String version);

  @Message(level = MessageLevel.INFO, text = "Deleted service definition {0} / {1} / {2}")
  void deletedServiceDefinitionChange(String serviceName, String role, String version);

  @Message(level = MessageLevel.ERROR, text = "Failed to save gateway status")
  void failedToSaveGatewayStatus();

  @Message(level = MessageLevel.ERROR, text = "Error validating topology {0}")
  void errorValidatingTopology(String topologyName);

  @Message(level = MessageLevel.ERROR, text = "Error parsing topology {0}")
  void errorParsingTopology(String topologyName);

  @Message(level = MessageLevel.ERROR, text = "Error creating provider configuration {0} from topology {1}, cause: {2}")
  void errorSavingProviderConfiguration(String providerPath, String topologyName, String message);

  @Message(level = MessageLevel.ERROR, text = "Error creating provider descriptor {0} from topology {1}, cause: {2}")
  void errorSavingDescriptorConfiguration(String providerPath, String topologyName, String message);

  @Message(level = MessageLevel.ERROR, text = "No service found by type {0}")
  void noServiceFound(String serviceType);

  @Message(level = MessageLevel.INFO, text = "Using {0} implementation for {1}")
  void usingServiceImplementation(String implementation, String serviceType);

  @Message(level = MessageLevel.ERROR, text = "Error while initiatalizing {0}: {1}")
  void errorInitializingService(String implementation, String error, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.ERROR,
          text = "Unable to complete service discovery for cluster {0} topology = {1}.")
  void failedToDiscoverClusterServices(String clusterName, String topologyName,
                                       @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.DEBUG, text = "Jetty's maxFormContentSize is set to {0}")
  void setMaxFormContentSize(int maxFormContentSize);

  @Message(level = MessageLevel.DEBUG, text = "Jetty's maxFormKeys is set to {0}")
  void setMaxFormKeys(int maxFormKeys);

  @Message(level = MessageLevel.ERROR, text = "ConcurrentSessionVerifier got blank username for verification.")
  void errorVerifyingUserBlankUsername();

  @Message(level = MessageLevel.ERROR, text = "ConcurrentSessionVerifier got blank username for token registration.")
  void errorRegisteringTokenForBlankUsername();

  @Message(level = MessageLevel.WARN, text = "InMemoryConcurrentSessionVerifier is used and privileged user group is not configured! Non-privileged limit applies to all users (except the unlimited group).")
  void privilegedUserGroupIsNotConfigured();

  @Message(level = MessageLevel.WARN,
          text = "Could not create local provider/descriptor config at {0}, cause: {1}")
  void errorSynchronizingLocalProviderDescriptor(LocalDirectory dir, Exception cause);

  @Message(level = MessageLevel.INFO,
          text = "Downloading provider/descriptor {0} to {1} from database")
  void downloadingProviderDescriptor(String name, LocalDirectory localDir);

  @Message(level = MessageLevel.INFO,
          text = "Deleting provider/descriptor {0} from directory: {1}")
  void deletingProviderDescriptor(String name, LocalDirectory localDir);

  @Message(level = MessageLevel.WARN,
          text = "Cannot create local directory: {0} cause: {1}")
  void cannotCreateLocalDirectory(File base, IOException e);

  @Message(level = MessageLevel.INFO,
          text = "DB remote configuration monitor. Interval = {0} seconds")
  void startingDbRemoteConfigurationMonitor(long intervalSeconds);

  @Message(level = MessageLevel.WARN,
          text = "Can not sync local file system from DB, cause = {0}")
  void errorWhileSyncingLocalFileSystem(Exception e);

  @Message(level = MessageLevel.DEBUG,
          text = "Remote configuration sync completed at: {0}")
  void remoteConfigurationSyncCompleted(Instant lastSyncTime);

  @Message(level = MessageLevel.DEBUG,
          text = "Deleting local {0} with name {1}")
  void deletingLocalDescriptorProvider(String type, String name);

  @Message(level = MessageLevel.DEBUG,
          text = "Creating local {0} with name {1}")
  void creatingLocalDescriptorProvider(String type, String name);

  @Message(level = MessageLevel.DEBUG,
          text = "Cleaning remote configuration db. Deleting logically deleted rows which are older than {0} seconds.")
  void cleaningRemoteConfigTables(int cleanUpPeriodSeconds);

  @Message(level = MessageLevel.INFO,
          text = "Initializing remote configuration db. Sync interval={0} seconds. Clean up interval={1} seconds.")
  void initDbRemoteConfigMonitor(long syncIntervalSeconds, int cleanUpPeriodSeconds);

  @Message(level = MessageLevel.DEBUG,
          text = "Request {0} is wrapped to url encoded form request.")
  void wrappingRequestToUrlEncodedFormRequest(String requestURI);

  @Message(level = MessageLevel.INFO,
          text = "Checking gateway status. Deployed topologies: {0}. Waiting for: {1}")
  void checkingGatewayStatus(Set<String> deployedTopologies, Set<String> missingTopologies);

  @Message(level = MessageLevel.INFO,
          text = "Checking gateway status. No topologies to check")
  void noTopologiesToCheck();

  @Message(level = MessageLevel.INFO,
          text = "Collected topologies for health check: {0}")
  void collectedTopologiesForHealthCheck(Set<String> result);

  @Message(level = MessageLevel.INFO,
          text = "Starting gateway status service. Topologies to check: {0}")
  void startingStatusMonitor(Set<String> topologyNames);

  @Message( level = MessageLevel.INFO, text = "Excluded \"{0}\" topology from client auth" )
  void topologyExcludedFromClientAuth( String topologyName );
}
