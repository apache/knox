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

import java.io.IOException;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.shell")
public interface KnoxShellMessages {
  @Message( level = MessageLevel.WARN, text = "Unable to create provided PEM encoded trusted cert - falling through for other truststores: {0}" )
  void unableToLoadProvidedPEMEncodedTrustedCert(@StackTrace( level = MessageLevel.DEBUG ) IOException e);

  @Message( level = MessageLevel.DEBUG, text = "No available Subject; Using JAAS configuration login" )
  void noSubjectAvailable();

  @Message( level = MessageLevel.DEBUG, text = "Using JAAS configuration file implementation: {0}" )
  void usingJAASConfigurationFileImplementation(String implName);

  @Message( level = MessageLevel.ERROR, text = "Failed to create JAAS configuration file implementation {0}: {1}" )
  void failedToLoadJAASConfigurationFileImplementation(String implName, String error);

  @Message( level = MessageLevel.ERROR, text = "Failed to instantiate JAAS configuration file implementation {0}: {1}" )
  void failedToInstantiateJAASConfigurationFileImplementation(String implName, String error);

  @Message( level = MessageLevel.ERROR, text = "No JAAS configuration file implementation is available" )
  void noJAASConfigurationFileImplementation();

  @Message( level = MessageLevel.ERROR, text = "Failed to create the JAAS configuration: {0}" )
  void failedToLoadJAASConfiguration(String configFileName);

  @Message( level = MessageLevel.ERROR, text = "Failed to locate the specified JAAS configuration: {0}" )
  void failedToLocateJAASConfiguration(String message);

  @Message( level = MessageLevel.ERROR, text = "The specified JAAS configuration does not exist: {0}" )
  void jaasConfigurationDoesNotExist(String jaasConf);

  @Message( level = MessageLevel.INFO, text = "Using default JAAS configuration" )
  void usingDefaultJAASConfiguration();

  @Message( level = MessageLevel.DEBUG, text = "JAAS configuration: {0}" )
  void jaasConfigurationLocation(String location);

  @Message( level = MessageLevel.WARN,
            text = "The javax.security.auth.useSubjectCredsOnly system property is set to 'false'; This may yield unexpected results with respect to Kerberos authentication." )
  void useSubjectCredsOnlyIsFalse();

}
