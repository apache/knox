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
package org.apache.hadoop.gateway;

import org.apache.commons.cli.ParseException;
import org.apache.hadoop.gateway.i18n.messages.Message;
import org.apache.hadoop.gateway.i18n.messages.MessageLevel;
import org.apache.hadoop.gateway.i18n.messages.Messages;
import org.apache.hadoop.gateway.i18n.messages.StackTrace;

import java.io.File;
import java.net.URI;

/**
 *
 */
@Messages(logger="org.apache.hadoop.gateway")
public interface GatewayMessages {

  @Message( level = MessageLevel.FATAL, text = "Failed to parse command line: {0}" )
  void failedToParseCommandLine( @StackTrace( level = MessageLevel.DEBUG ) ParseException e );

  @Message( level = MessageLevel.INFO, text = "Starting gateway..." )
  void startingGateway();

  @Message( level = MessageLevel.FATAL, text = "Failed to start gateway: {0}" )
  void failedToStartGateway( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.INFO, text = "Started gateway on port {0}." )
  void startedGateway( int port );

  @Message( level = MessageLevel.INFO, text = "Stopping gateway..." )
  void stoppingGateway();

  @Message( level = MessageLevel.INFO, text = "Stopped gateway." )
  void stoppedGateway();

  @Message( level = MessageLevel.INFO, text = "Loading configuration resource {0}" )
  void loadingConfigurationResource( String res );

  @Message( level = MessageLevel.INFO, text = "Loading configuration file {0}" )
  void loadingConfigurationFile( String file );

  @Message( level = MessageLevel.WARN, text = "Failed to load configuration file {0}: {1}" )
  void failedToLoadConfig( String path, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.INFO, text = "Using {1} as GATEWAY_HOME via {0}." )
  void settingGatewayHomeDir( String location, String home );

  @Message( level = MessageLevel.INFO, text = "Loading topologies from directory: {0}" )
  void loadingTopologiesFromDirecotry( String topologiesDir );

  @Message( level = MessageLevel.DEBUG, text = "Loading topology file: {0}" )
  void loadingTopologyFile( String fileName );

  @Message( level = MessageLevel.INFO, text = "Monitoring topologies in directory: {0}" )
  void monitoringTopologyChangesInDirectory( String topologiesDir );

  @Message( level = MessageLevel.INFO, text = "Deploying topology {0} to {1}" )
  void deployingTopology( String clusterName, String warDirName );

  @Message( level = MessageLevel.INFO, text = "Deployed topology {0}." )
  void deployedTopology( String clusterName );

  @Message( level = MessageLevel.INFO, text = "Loading topology {0} from {1}" )
  void redeployingTopology( String clusterName, String warDirName );

  @Message( level = MessageLevel.INFO, text = "Redeployed topology {0}." )
  void redeployedTopology( String clusterName );

  @Message( level = MessageLevel.ERROR, text = "Failed to deploy topology {0}: {1}" )
  void failedToDeployTopology( String name, @StackTrace(level=MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to undeploy topology {0}: {1}" )
  void failedToUndeployTopology( String name, @StackTrace(level=MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.INFO, text = "Deleting deployed topology {0}" )
  void deletingDeployment( String warDirName );

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

  @Message( level = MessageLevel.INFO, text = "Keystore for the gateway instance found - no need to create one." )
  void keyStoreForGatewayFoundNotCreating();

  @Message( level = MessageLevel.INFO, text = "Creating credential store for the cluster: {0}" )
  void creatingCredentialStoreForCluster(String clusterName);

  @Message( level = MessageLevel.INFO, text = "Credential store found for the cluster: {0} - no need to create one." )
  void credentialStoreForClusterFoundNotCreating(String clusterName);

  @Message( level = MessageLevel.DEBUG, text = "Dispatching request: {0} {1}" )
  void dispatchRequest( String method, URI uri );

}
