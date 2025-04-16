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

### HadoopAuth Authentication Provider ###
The HadoopAuth authentication provider for Knox integrates the use of the Apache Hadoop module for SPNEGO and delegation token based authentication. This introduces the same authentication pattern used across much of the Hadoop ecosystem to Apache Knox and allows clients to using the strong authentication and SSO capabilities of Kerberos.

#### Configuration ####
##### Overview #####
As with all providers in the Knox gateway, the HadoopAuth provider is configured through provider parameters. The configuration parameters are the same parameters used within Apache Hadoop for the same capabilities. In this section, we provide an example configuration and description of each of the parameters. We do encourage the reader to refer to the Hadoop documentation for this as well. (see http://hadoop.apache.org/docs/current/hadoop-auth/Configuration.html)

One of the interesting things to note about this configuration is the use of the `config.prefix` parameter. In Hadoop there may be multiple components with their own specific configuration values for these parameters and since they may get mixed into the same Configuration object - there needs to be a way to identify the component specific values. The `config.prefix` parameter is used for this and is prepended to each of the configuration parameters for this provider. Below, you see an example configuration where the value for config.prefix happens to be `hadoop.auth.config`. You will also notice that this same value is prepended to the name of the rest of the configuration parameters.

    <provider>
      <role>authentication</role>
      <name>HadoopAuth</name>
      <enabled>true</enabled>
      <param>
        <name>config.prefix</name>
        <value>hadoop.auth.config</value>
      </param>
      <param>
        <name>hadoop.auth.config.signature.secret</name>
        <value>knox-signature-secret</value>
      </param>
      <param>
        <name>hadoop.auth.config.type</name>
        <value>kerberos</value>
      </param>
      <param>
        <name>hadoop.auth.config.simple.anonymous.allowed</name>
        <value>false</value>
      </param>
      <param>
        <name>hadoop.auth.config.token.validity</name>
        <value>1800</value>
      </param>
      <param>
        <name>hadoop.auth.config.cookie.domain</name>
        <value>novalocal</value>
      </param>
      <param>
        <name>hadoop.auth.config.cookie.path</name>
        <value>gateway/default</value>
      </param>
      <param>
        <name>hadoop.auth.config.kerberos.principal</name>
        <value>HTTP/lmccay-knoxft-24m-r6-sec-160422-1327-2.novalocal@EXAMPLE.COM</value>
      </param>
      <param>
        <name>hadoop.auth.config.kerberos.keytab</name>
        <value>/etc/security/keytabs/spnego.service.keytab</value>
      </param>
      <param>
        <name>hadoop.auth.config.kerberos.name.rules</name>
        <value>DEFAULT</value>
      </param>
    </provider>
  

#### Descriptions ####
The following tables describes the configuration parameters for the HadoopAuth provider:

###### Config

Name | Description | Default
---------|-----------|----
config.prefix            | If specified, all other configuration parameter names must start with the prefix. | none
signature.secret|This is the secret used to sign the delegation token in the hadoop.auth cookie. This same secret needs to be used across all instances of the Knox gateway in a given cluster. Otherwise, the delegation token will fail validation and authentication will be repeated each request. | A simple random number  
type                     | This parameter needs to be set to `kerberos` | none, would throw exception
simple.anonymous.allowed | This should always be false for a secure deployment. | true
token.validity           | The validity -in seconds- of the generated authentication token. This is also used for the rollover interval when `signer.secret.provider` is set to random or ZooKeeper. | 36000 seconds
cookie.domain            | Domain to use for the HTTP cookie that stores the authentication token | null
cookie.path              | Path to use for the HTTP cookie that stores the authentication token | null
kerberos.principal       | The web-application Kerberos principal name. The Kerberos principal name must start with HTTP/.... For example: `HTTP/localhost@LOCALHOST` | null
kerberos.keytab          | The path to the keytab file containing the credentials for the kerberos principal. For example: `/Users/lmccay/lmccay.keytab` | null
kerberos.name.rules      | The name of the ruleset for extracting the username from the kerberos principal. | DEFAULT

###### REST Invocation
Once a user logs in with kinit then their Kerberos session may be used across client requests with things like curl.
The following curl command can be used to request a directory listing from HDFS while authenticating with SPNEGO via the `--negotiate` flag

    curl -k -i --negotiate -u : https://localhost:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS


