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
import org.apache.hadoop.gateway.i18n.messages.Messages;
import org.apache.hadoop.gateway.i18n.messages.StackTrace;

import java.io.File;
import java.net.URL;

import static org.apache.hadoop.gateway.i18n.messages.MessageLevel.*;

/**
 *
 */
@Messages
public interface GatewayMessages {

  @Message(level=FATAL, text="Failed to parse command line: {0}" )
  void failedToParseCommandLine( @StackTrace(level=DEBUG) ParseException e );

  @Message(level=INFO, text="Starting gateway..." )
  void startingGateway();

  @Message(level=FATAL, text="Failed to start gateway: {0}" )
  void failedToStartGateway( @StackTrace(level=DEBUG) Exception e );

  @Message(level=INFO, text="Started gateway on port {0}." )
  void startedGateway( int port );

  @Message(level=INFO, text="Stopping gateway..." )
  void stoppingGateway();

  @Message(level=INFO, text="Stopped gateway." )
  void stoppedGateway();

  @Message(level=INFO, text="Loading configuration resource {0}" )
  void loadingConfigurationResource( String res );

  @Message(level=INFO, text="Loading configuration file {0}" )
  void loadingConfigurationFile( String file );

  @Message(level=WARN, text="Failed to load configuration file {0}: {1}" )
  void failedToLoadConfig( String path, @StackTrace(level=DEBUG) Exception e );

  @Message(level=INFO, text="Using {1} as GATEWAY_HOME via {0}." )
  void settingGatewayHomeDir( String location, String home );

  @Message(level=INFO, text="Loading topology files from directory: {0}" )
  void loadingTopologiesFromDirecotry( String topologiesDir );

  @Message(level=INFO, text="Monitoring topology files in directory: {0}" )
  void monitoringTopologyChangesInDirectory( String topologiesDir );

  @Message(level=INFO, text="Deploying cluster topology {0} to {1}" )
  void deployingCluster( String clusterName, String warDirName );

  @Message(level=INFO, text="Deployed cluster topology {0}." )
  void deployedCluster( String clusterName );

  @Message(level=INFO, text="Deleting deployed cluster topology {0}" )
  void deletingCluster( String warDirName );

  @Message(level=INFO, text="Creating gateway home directory: {0}" )
  void creatingGatewayHomeDir( File homeDir );

  @Message(level=INFO, text="Creating gateway cluster topology deployment directory: {0}" )
  void creatingGatewayDeploymentDir( File topologiesDir );

  @Message(level=INFO, text="Creating default gateway configuration file: {0}" )
  void creatingDefaultConfigFile( File defaultConfigFile );

  @Message(level=INFO, text="Creating sample cluster topology file: {0}" )
  void creatingDefaultTopologyFile( File defaultConfigFile );

}
