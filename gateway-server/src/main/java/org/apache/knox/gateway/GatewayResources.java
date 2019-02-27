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

import org.apache.knox.gateway.i18n.resources.Resource;
import org.apache.knox.gateway.i18n.resources.Resources;

@Resources
public interface GatewayResources {

  @Resource( text="Apache Knox Gateway {0} ({1})" )
  String gatewayVersionMessage( String version, String hash );

  @Resource( text="Apache Knox Gateway" )
  String gatewayServletInfo();

  @Resource( text="Service connectivity error." )
  String dispatchConnectionError();

  @Resource( text="Display command line help." )
  String helpMessage();

  @Resource( text="This parameter causes the server to exit before starting to service requests. This is typically used with the -persist-master parameter." )
  String nostartHelpMessage();

  @Resource( text="This parameter causes the provider master secret to be persisted. This prevents the server from prompting for a master secret on subsequent starts." )
  String persistMasterHelpMessage();

  @Resource( text="This parameter causes the existing topologies to be redeployed. A single topology may be specified via an optional parameter.  The server will not be started." )
  String redeployHelpMessage();

  @Resource( text="Display server version information." )
  String versionHelpMessage();

  @Resource( text="Topology is required." )
  String topologyIsRequiredError();

  @Resource( text="Provider is required." )
  String providerIsRequiredError();

  @Resource( text="Unsupported property''s token: {0}" )
  String unsupportedPropertyTokenError(String token);

  @Resource( text="Failed to build topology: wrong data format." )
  String wrongTopologyDataFormatError();

  @Resource( text="Provider parameter name is required." )
  String providerParameterNameIsRequiredError();

  @Resource( text="Provider parameter value is required." )
  String providerParameterValueIsRequiredError();

  @Resource( text="Service parameter name is required." )
  String serviceParameterNameIsRequiredError();

  @Resource( text="Service parameter value is required." )
  String serviceParameterValueIsRequiredError();

  @Resource( text="Failed to create keystore directory: {0}" )
  String failedToCreateKeyStoreDirectory( String name );

  @Resource( text="Response status: {0}" )
  String responseStatus( int status );

  @Resource( text="Request method: {0}" )
  String requestMethod( String method );

  @Resource( text="Forward method: {0} to default context: {1}" )
  String forwardToDefaultTopology(String method, String context );

  @Resource( text="The keystore used to acquire the signing key is not available. The file could be missing or the password could be incorrect: {0}")
  String signingKeystoreNotAvailable( String path );

  @Resource( text="Provisioned signing key passphrase cannot be acquired using the alias name {0}.")
  String signingKeyPassphraseNotAvailable( String alias);

  @Resource( text="The public signing key was not found in the signing keystore using the alias name {0}.")
  String publicSigningKeyNotFound( String alias );

  @Resource( text="The private signing key was not found in the signing keystore using the alias name {0}. The alias could be missing or the password could be incorrect.")
  String privateSigningKeyNotFound( String alias );

  @Resource( text="The private signing key found in the signing keystore using the alias name {0} is not a RSAPrivateKey")
  String privateSigningKeyWrongType( String alias );

  @Resource( text="The public signing key found in the signing keystore using the alias name {0} is not a RSAPublicKey")
  String publicSigningKeyWrongType( String alias );
}
