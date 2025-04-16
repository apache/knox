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

### Knox CLI ###
The Knox CLI is a command line utility for the management of various aspects of the Knox deployment. It is primarily concerned with the management of the security artifacts for the gateway instance and each of the deployed topologies or Hadoop clusters that are gated by the Knox Gateway instance.

The various security artifacts are also generated and populated automatically by the Knox Gateway runtime when they are not found at startup. The assumptions made in those cases are appropriate for a test or development gateway instance and assume 'localhost' for hostname specific activities. For production deployments the use of the CLI may aid in managing some production deployments.

The `knoxcli.sh` script is located in the `{GATEWAY_HOME}/bin` directory.

#### Help ####
##### `bin/knoxcli.sh [--help]` #####
prints help for all commands

#### Knox Version Info ####
##### `bin/knoxcli.sh version [--help]` #####
Displays Knox version information.

#### Master secret persistence ####
##### `bin/knoxcli.sh create-master [--force] [--master mastersecret] [--generate]` #####
The create-master command persists the master secret in a file located at: `{GATEWAY_HOME}/data/security/master`.

It will prompt the user for the secret to persist.

Use `--force` to overwrite the master secret.

Use `--master` to pass in a master secret to persist. This can be used to persist the secret without any user interaction. Be careful as the secret might appear in shell histories or process listings.<br/>
Instead of `--master` it is usually a better idea to use `--generate` instead!

Use `--generate` to have Knox automatically generate a random secret.
The generated secret will not be printed or otherwise exposed.

Do not specify both `--master` and `--generate` at the same time.

NOTE: This command fails when there is an existing master file in the expected location. You may force it to overwrite the master file with the \-\-force switch. NOTE: this will require you to change passwords protecting the keystores for the gateway identity keystores and all credential stores.

#### Alias creation ####
##### `bin/knoxcli.sh create-alias name [--cluster c] [--value v] [--generate] [--help]` #####
Creates a password alias and stores it in a credential store within the `{GATEWAY_HOME}/data/security/keystores` dir.

Argument     | Description
-------------|-----------
name         | Name of the alias to create
\-\-cluster  | Name of Hadoop cluster for the cluster specific credential store otherwise assumes that it is for the gateway itself
\-\-value    | Parameter for specifying the actual password otherwise prompted. Escape complex passwords or surround with single quotes
\-\-generate | Boolean flag to indicate whether the tool should just generate the value. This assumes that \-\-value is not set - will result in error otherwise. User will not be prompted for the value when \-\-generate is set.

#### Batch alias creation ####

##### `bin/knoxcli.sh create-aliases --alias alias1 [--value value1] --alias alias2 [--value value2] --alias aliasN [--value valueN] ... [--cluster clustername] [--generate]` #####

Creates multiple password aliases and stores them in a credential store within the `{GATEWAY_HOME}/data/security/keystores` dir.

Argument     | Description
-------------|-----------
\-\-alias    | Name of an alias to create.
\-\-value    | Parameter for specifying the actual password otherwise prompted. Escape complex passwords or surround with single quotes.
\-\-generate | Boolean flag to indicate whether the tool should just generate the value. This assumes that \-\-value is not set - will result in error otherwise. User will not be prompted for the value when \-\-generate is set.
\-\-cluster  | Name of Hadoop cluster for the cluster specific credential store otherwise assumes that it is for the gateway itself

#### Alias deletion ####
##### `bin/knoxcli.sh delete-alias name [--cluster c] [--help]` #####
Deletes a password and alias mapping from a credential store within `{GATEWAY_HOME}/data/security/keystores`.

Argument    | Description
------------|-----------
name        | Name of the alias to delete
\-\-cluster | Name of Hadoop cluster for the cluster specific credential store otherwise assumes '__gateway'

#### Alias listing ####
##### `bin/knoxcli.sh list-alias [--cluster c] [--help]` #####
Lists the alias names for the credential store within `{GATEWAY_HOME}/data/security/keystores`.

NOTE: This command will list the aliases in lowercase which is a result of the underlying credential store implementation. Lookup of credentials is a case insensitive operation - so this is not an issue.

Argument    | Description
------------|-----------
\-\-cluster | Name of Hadoop cluster for the cluster specific credential store otherwise assumes '__gateway'

#### Self-signed cert creation ####
##### `bin/knoxcli.sh create-cert [--hostname n] [--help]` #####
Creates and stores a self-signed certificate to represent the identity of the gateway instance. This is stored within the `{GATEWAY_HOME}/data/security/keystores/gateway.jks` keystore.

Argument     | Description
-------------|-----------
\-\-hostname | Name of the host to be used in the self-signed certificate. This allows multi-host deployments to specify the proper hostnames for hostname verification to succeed on the client side of the SSL connection. The default is 'localhost'.

#### Certificate Export ####
##### `bin/knoxcli.sh export-cert [--type JKS|PEM|JCEKS|PKCS12] [--help]` #####
The export-cert command exports the public certificate from the a gateway.jks keystore with the alias of gateway-identity. It will be exported to `{GATEWAY_HOME}/data/security/keystores/` with a name of `gateway-client-trust.<type>`. Using the `--type` option you can specify which keystore type you need (default: PEM)

**NOTE:** The password for the JKS, JCEKS and PKCS12 types is `changeit`. It can be changed using: `keytool -storepasswd -storetype <type> -keystore gateway-client-trust.<type>`

#### Topology Redeploy ####
##### `bin/knoxcli.sh redeploy [--cluster c]` #####
Redeploys one or all of the gateway's clusters (a.k.a topologies).

#### Topology Listing ####
##### `bin/knoxcli.sh list-topologies [--help]` ####
Lists all of the topologies found in Knox's topologies directory. Useful for specifying a valid --cluster argument.

#### Topology Validation ####
##### `bin/knoxcli.sh validate-topology [--cluster c] [--path path] [--help]` ####
This ensures that a cluster's description (a.k.a. topology) follows the correct formatting rules. It is possible to specify a name of a cluster already in the topology directory, or a path to any file.

Argument    | Description
------------|-----------
\-\-cluster | Name of Hadoop cluster for which you want to validate
\-\-path    | Path to topology file that you wish to validate.

#### LDAP Authentication and Authorization ####
##### `bin/knoxcli.sh user-auth-test [--cluster c] [--u username] [--p password] [--g] [--d] [--help]` ####
This command will test a topology's ability to connect, authenticate, and authorize a user with an LDAP server. The only required argument is the --cluster argument to specify the name of the topology you wish to use. The topology must be valid (passes validate-topology command). If a `--u` and `--p` argument are not specified, the command line will prompt for a username and password. If authentication is successful then the command will attempt to use the topology to do an LDAP group lookup. The topology must be configured correctly to do this. If it is not, groups will not return and no errors will be printed unless the `--g` command is specified. Currently this command only works if a topology supports the use of ShiroProvider for authentication.

Argument    | Description
------------|-----------
\-\-cluster | Required; Name of cluster for which you want to test authentication
\-\-u       | Optional; Username you wish you authenticate with
\-\-p       | Optional; Password you wish to authenticate with
\-\-g       | Optional; Specify that you are looking to return a user's groups. If not specified, group lookup errors won't return
\-\-d       | Optional; Print extra debug info on failed authentication

#### Topology LDAP Bind ####
##### `bin/knoxcli.sh system-user-auth-test [--cluster c] [--d] [--help]` ####
This command will test a given topology's ability to connect, bind, and authenticate with the LDAP server from the settings specified in the topology file. The bind currently only will with Shiro as the authentication provider. There are also two parameters required inside of the topology for these

Argument    | Description
------------|-----------
\-\-cluster | Required; Name of cluster for which you want to test authentication
\-\-d       | Optional; Print extra debug info on failed authentication


#### Gateway Service Test ####
##### `bin/knoxcli.sh service-test [--cluster c] [--hostname hostname] [--port port] [--u username] [--p password] [--d] [--help]` ####

This will test a topology configuration's ability to connect to multiple Hadoop services. Each service found in a topology will be tested with multiple URLs. Results are printed to the console in JSON format.

Argument     | Description
-------------|-----------
\-\-cluster  | Required; Name of cluster for which you want to test authentication
\-\-hostname | Required; Hostname of the cluster currently running on the machine
\-\-port     | Optional; Port that the cluster is running on. If not supplied CLI will try to read config files to find the port.
\-\-u        | Required; Username to authorize against Hadoop services
\-\-p        | Required; Password to match username
\-\-d        | Optional; Print extra debug info on failed authentication

#### Remote Configuration Registry Client Listing ####
##### `bin/knoxcli.sh list-registry-clients` #####

Lists the [remote configuration registry clients](#Remote+Configuration+Registry+Clients) defined in '{GATEWAY_HOME}/conf/gateway-site.xml'.


#### List Provider Configurations in a Remote Configuration Registry ####
##### `bin/knoxcli.sh list-provider-configs --registry-client name` ####

List the provider configurations in the remote configuration registry for which the referenced client provides access.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml


#### List Descriptors in a Remote Configuration Registry ####
##### `bin/knoxcli.sh list-descriptors --registry-client name` ####

List the descriptors in the remote configuration registry for which the referenced client provides access.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml


#### Upload Provider Configuration to a Remote Configuration Registry ####
##### `bin/knoxcli.sh upload-provider-config providerConfigFile --registry-client name [--entry-name entryName]` ####

Upload a provider configuration file to the remote configuration registry for which the referenced client provides access.
By default, the entry name will be the same as the uploaded file's name.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml
\-\-entry\-name	| Optional; The name of the entry for the uploaded content in the registry.


#### Upload Descriptor to a Remote Configuration Registry ####
##### `bin/knoxcli.sh upload-descriptor descriptorFile --registry-client name [--entry-name entryName]` ####

Upload a descriptor file to the remote configuration registry for which the referenced client provides access.
By default, the entry name will be the same as the uploaded file's name.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml
\-\-entry\-name	| Optional; The name of the entry for the uploaded content in the registry.


#### Delete a Provider Configuration From a Remote Configuration Registry ####
##### `bin/knoxcli.sh delete-provider-config providerConfig --registry-client name` ####

Delete a provider configuration from the remote configuration registry for which the referenced client provides access.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml


#### Delete a Descriptor From a Remote Configuration Registry ####
##### `bin/knoxcli.sh delete-descriptor descriptor --registry-client name` ####

Delete a descriptor from the remote configuration registry for which the referenced client provides access.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml


#### Get the ACL For an Entry in a Remote Configuration Registry ####
##### `bin/knoxcli.sh get-registry-acl entry --registry-client name` ####

List the ACL set for the specified entry in the remote configuration registry for which the referenced client provides access.

Argument | Description
---------|-----------
\-\-registry\-client | Required; The name of a [remote configuration registry client](#Remote+Configuration+Registry+Clients), as defined in gateway-site.xml

#### Convert topology file to provider and descriptor config files ####
##### `bin/knoxcli.sh convert-topology --path path/to/topology.xml --provider-name my-prov.json [--descriptor-name my-desc.json] ` ####

Convert topology xml files to provider and descriptor config files.

Argument | Description
---------|-----------
\-\-path | Required; Path to topology xml file.
\-\-provider\-name | Required; Name of the provider json config file (including .json extension).
\-\-descriptor\-name | Optional; Name of descriptor json config file (including .json extension).
\-\-topology\-name | Optional; topology-name can be use instead of \-\-path option, if used, KnoxCLI will attempt to find topology from deployed topologies directory. If not provided, topology name will be used as descriptor name
\-\-output\-path | Optional; Output directory to save provider and descriptor config files if not provided, config files will be saved in appropriate Knox config directory.
\-\-force | Optional; Force rewriting of existing files, if not used, command will fail when the config files with same name already exist.
\-\-cluster | Optional; Cluster name, required for service discovery.
\-\-discovery\-url | Optional; Service discovery URL, required for service discovery.
\-\-discovery\-user | Optional; Service discovery user, required for service discovery.
\-\-discovery\-pwd\-alias | Optional; Password alias for service discovery user, required for service discovery.
\-\-discovery\-type | Optional; Service discovery type, required for service discovery.


