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
package org.apache.knox.gateway.i18n;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway")
public interface GatewaySpiMessages {

  @Message(level = MessageLevel.ERROR, text = "Failed to load the internal principal mapping table: {0}" )
  void failedToLoadPrincipalMappingTable( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to execute filter: {0}" )
  void failedToExecuteFilter( @StackTrace( level = MessageLevel.DEBUG ) Throwable t );

  @Message( level = MessageLevel.ERROR, text = "Failed to encrypt passphrase: {0}" )
  void failedToEncryptPassphrase( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to generate secret key from password: {0}" )
  void failedToGenerateKeyFromPassword( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to persist master secret: {0}" )
  void failedToPersistMasterSecret( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to encrypt master secret: {0}" )
  void failedToEncryptMasterSecret( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize master service from persistent master {0}: {1}" )
  void failedToInitializeFromPersistentMaster( String masterFileName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to add self signed certificate for Gateway {0}: {1}" )
  void failedToAddSeflSignedCertForGateway( String alias, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to get key {0}: {1}" )
  void failedToGetKey(String alias, @StackTrace( level = MessageLevel.DEBUG ) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Loading from persistent master: {0}" )
  void loadingFromPersistentMaster( String tag );

  @Message( level = MessageLevel.DEBUG, text = "ALIAS: {0}" )
  void printClusterAlias( String alias );

  @Message( level = MessageLevel.DEBUG, text = "MASTER SERVICE == NULL: {0}" )
  void printMasterServiceIsNull( boolean masterServiceIsNull );

  @Message( level = MessageLevel.ERROR, text = "Gateway has failed to start. Unable to prompt user for master secret setup. Please consider using knoxcli.sh create-master" )
  void unableToPromptForMasterUseKnoxCLI();

  @Message(level = MessageLevel.ERROR, text = "Failed to read configuration: {0}")
  void failedToReadConfigurationFile(String filePath, @StackTrace(level = MessageLevel.DEBUG) Exception e );

  @Message(level = MessageLevel.ERROR, text = "Invalid resource URI {0} : {1}")
  void invalidResourceURI(String uri, String reason, @StackTrace(level = MessageLevel.DEBUG) Exception e );

  @Message(level = MessageLevel.ERROR, text = "Invalid resource name: {0}")
  void invalidResourceName(String resourceName);

  @Message( level = MessageLevel.ERROR, text = "Topology {0} cannot be manually overwritten because it was generated from a simple descriptor." )
  void disallowedOverwritingGeneratedTopology(String topologyName);

  @Message(level = MessageLevel.ERROR, text = "Failed to load truststore due to {0}")
  void failedToLoadTruststore(String message, @StackTrace(level = MessageLevel.DEBUG) Exception e);

  @Message(level = MessageLevel.WARN, text = "Duplicated filter param key: {0}")
  void duplicatedFilterParamKey(String name);
}
