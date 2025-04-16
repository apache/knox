<!---
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
--->

### Mutual Authentication with SSL ###

To establish a stronger trust relationship between client and server, we provide mutual authentication with SSL via client certs. This is particularly useful in providing additional validation for Preauthenticated SSO with HTTP Headers. Rather than just IP address validation, connections will only be accepted by Knox from clients presenting trusted certificates.

This behavior is configured for the entire gateway instance within the gateway-site.xml file. All topologies deployed within the configured gateway instance will require incoming connections to present trusted client certificates during the SSL handshake. Otherwise, connections will be refused.

The following table describes the configuration elements related to mutual authentication and their defaults:

| Configuration Element                          | Description                                               |
| -----------------------------------------------|-----------------------------------------------------------|
| gateway.client.auth.needed                     | True\|False - indicating the need for client authentication. Default is False.|
| gateway.truststore.path                        | Fully qualified path to the trust store to use. Default is the keystore used to hold the Gateway's identity.  See `gateway.tls.keystore.path`.|
| gateway.truststore.type                        | Keystore type of the trust store. Default is JKS.         |
| gateway.truststore.password.alias              | Alias for the password to the trust store.|
| gateway.trust.all.certs                        | Allows for all certificates to be trusted. Default is false.|

By only indicating that it is needed with `gateway.client.auth.needed`, the keystore identified by `gateway.tls.keystore.path` is used.  By default this is `{GATEWAY_HOME}/data/security/keystores/gateway.jks`. 
This is the identity keystore for the server, which can also be used as the truststore.
To use a dedicated truststore, `gateway.truststore.path` may be set to the absolute path of the truststore file.  
The type of truststore file should be set using `gateway.truststore.type`; else, JKS will be assumed.  
If the truststore password is different from the Gateway's master secret then it can be set using

    knoxcli.sh create-alias {password-alias} --value {pwd} 
  
The password alias name (`{password-alias}`) is set using `gateway.truststore.password.alias`; else, the alias name of "gateway-truststore-password" should be used.  
If a password is not found using the provided (or default) alias name, then the Gateway's master secret will be used.

#### Exclude a Topology from mTLS ####

There is a possibility to exclude specific topologies from mutual authentication.

Configuration Element | Description
----------------------|----------------------
`gateway.client.auth.needed`| True - Indicating the need for client authentication.
`gateway.port.mapping.enabled`| True - Enabling the port mapping feature. It is turned on by default.
`gateway.port.mapping.{topologyName}`| The port number that this topology will listen on.
`gateway.client.auth.exclude`| The names of the topologies separated by comma. These topologies will be excluded from mTLS.

To exclude a topology from mTLS we use the port mapping feature. The `gateway.port.mapping.enabled` feature has to be enabled which is the default behaviour and a port number has to be provided for the topology with the `gateway.port.mapping.{topologyName}` property. The same topology needs to be added to the `gateway.client.auth.exclude` property.

The below example excludes the `health` topology from mTLS on the 9443 port.

      <property>
          <name>gateway.port.mapping.health</name>
          <value>9443</value>
          <description>Topology and Port mapping</description>
      </property>
      <property>
          <name>gateway.client.auth.exclude</name>
          <value>health</value>
          <description>Topology excluded from mTLS</description>
      </property>

An example how one can access the health topology on port 9443 without mTLS.

     https://{gateway-host}:9443/{gateway-path}/health
