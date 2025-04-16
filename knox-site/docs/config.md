<!--
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
-->

### Configuration ###

### Related Cluster Configuration ###

The following configuration changes must be made to your cluster to allow Apache Knox to
dispatch requests to the various service components on behalf of end users.

#### Grant Proxy privileges for Knox user in `core-site.xml` on Hadoop master nodes ####

Update `core-site.xml` and add the following lines towards the end of the file.

Replace `FQDN_OF_KNOX_HOST` with the fully qualified domain name of the host running the Knox gateway.
You can usually find this by running `hostname -f` on that host.

You can use `*` for local developer testing if the Knox host does not have a static IP.

    <property>
        <name>hadoop.proxyuser.knox.groups</name>
        <value>users</value>
    </property>
    <property>
        <name>hadoop.proxyuser.knox.hosts</name>
        <value>FQDN_OF_KNOX_HOST</value>
    </property>

#### Grant proxy privilege for Knox in `webhcat-site.xml` on Hadoop master nodes ####

Update `webhcat-site.xml` and add the following lines towards the end of the file.

Replace `FQDN_OF_KNOX_HOST` with the fully qualified domain name of the host running the Knox gateway.
You can use `*` for local developer testing if the Knox host does not have a static IP.

    <property>
        <name>webhcat.proxyuser.knox.groups</name>
        <value>users</value>
    </property>
    <property>
        <name>webhcat.proxyuser.knox.hosts</name>
        <value>FQDN_OF_KNOX_HOST</value>
    </property>

#### Grant proxy privilege for Knox in `oozie-site.xml` on Oozie host ####

Update `oozie-site.xml` and add the following lines towards the end of the file.

Replace `FQDN_OF_KNOX_HOST` with the fully qualified domain name of the host running the Knox gateway.
You can use `*` for local developer testing if the Knox host does not have a static IP.

    <property>
        <name>oozie.service.ProxyUserService.proxyuser.knox.groups</name>
        <value>users</value>
    </property>
    <property>
        <name>oozie.service.ProxyUserService.proxyuser.knox.hosts</name>
        <value>FQDN_OF_KNOX_HOST</value>
    </property>

#### Enable http transport mode and use substitution in HiveServer2 ####

Update `hive-site.xml` and set the following properties on HiveServer2 hosts.
Some of the properties may already be in the hive-site.xml. 
Ensure that the values match the ones below.

    <property>
        <name>hive.server2.allow.user.substitution</name>
        <value>true</value>
    </property>

    <property>
        <name>hive.server2.transport.mode</name>
        <value>http</value>
        <description>Server transport mode. "binary" or "http".</description>
    </property>

    <property>
        <name>hive.server2.thrift.http.port</name>
        <value>10001</value>
        <description>Port number when in HTTP mode.</description>
    </property>

    <property>
        <name>hive.server2.thrift.http.path</name>
        <value>cliservice</value>
        <description>Path component of URL endpoint when in HTTP mode.</description>
    </property>

#### Gateway Server Configuration ####

The following table illustrates the configurable elements of the Apache Knox Gateway at the server level via gateway-site.xml.

Property    | Description | Default
------------|-----------|-----------
`gateway.deployment.dir`|The directory within `GATEWAY_HOME` that contains gateway topology deployments|`{GATEWAY_HOME}/data/deployments`
`gateway.security.dir`|The directory within `GATEWAY_HOME` that contains the required security artifacts|`{GATEWAY_HOME}/data/security`
`gateway.data.dir`|The directory within `GATEWAY_HOME` that contains the gateway instance data|`{GATEWAY_HOME}/data`
`gateway.services.dir`|The directory within `GATEWAY_HOME` that contains the gateway services definitions|`{GATEWAY_HOME}/services`
`gateway.hadoop.conf.dir`|The directory within `GATEWAY_HOME` that contains the gateway configuration|`{GATEWAY_HOME}/conf`
`gateway.frontend.url`|The URL that should be used during rewriting so that it can rewrite the URLs with the correct "frontend" URL|none
`gateway.server.header.enabled`|Indicates whether Knox displays service info in HTTP response|`false`
`gateway.xforwarded.enabled`|Indicates whether support for some X-Forwarded-* headers is enabled|`true`
`gateway.trust.all.certs`|Indicates whether all presented client certs should establish trust|`false`
`gateway.client.auth.needed`|Indicates whether clients are required to establish a trust relationship with client certificates|`false`
`gateway.truststore.password.alias`|OPTIONAL Alias for the password to the truststore file holding the trusted client certificates. NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the master secret will be used.|`gateway-truststore-password`
`gateway.truststore.path`|Location of the truststore for client certificates to be trusted|`null`
`gateway.truststore.type`|Indicates the type of truststore at the path declared in `gateway.truststore.path`|`JKS`
`gateway.jdk.tls.ephemeralDHKeySize`|`jdk.tls.ephemeralDHKeySize`, is defined to customize the ephemeral DH key sizes. The minimum acceptable DH key size is 1024 bits, except for exportable cipher suites or legacy mode (`jdk.tls.ephemeralDHKeySize=legacy`)|`2048`
`gateway.threadpool.max`|The maximum concurrent requests the server will process. The default is 254. Connections beyond this will be queued.|`254`
`gateway.httpclient.connectionTimeout`|The amount of time to wait when attempting a connection. The natural unit is milliseconds, but a 's' or 'm' suffix may be used for seconds or minutes respectively.| `20s`
`gateway.httpclient.maxConnections`|The maximum number of connections that a single HttpClient will maintain to a single host:port.|`32`
`gateway.httpclient.socketTimeout`|The amount of time to wait for data on a socket before aborting the connection. The natural unit is milliseconds, but a 's' or 'm' suffix may be used for seconds or minutes respectively.| `20s`
`gateway.httpclient.truststore.password.alias`|OPTIONAL Alias for the password to the truststore file holding the trusted service certificates. NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the master secret will be used.|`gateway-httpclient-truststore-password`
`gateway.httpclient.truststore.path`|Location of the truststore for service certificates to be trusted|`null`
`gateway.httpclient.truststore.type`|Indicates the type of truststore at the path declared in `gateway.httpclient.truststore.path`|`JKS`
`gateway.httpserver.requestBuffer`|The size of the HTTP server request buffer in bytes|`16384`
`gateway.httpserver.requestHeaderBuffer`|The size of the HTTP server request header buffer in bytes|`8192`
`gateway.httpserver.responseBuffer`|The size of the HTTP server response buffer in bytes|`32768`
`gateway.httpserver.responseHeaderBuffer`|The size of the HTTP server response header buffer in bytes|`8192`
`gateway.websocket.feature.enabled`|Enable/Disable WebSocket feature|`false`
`gateway.tls.keystore.password.alias`|OPTIONAL Alias for the password to the keystore file holding the Gateway's TLS certificate and keypair. NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the master secret will be used.|`gateway-identity-keystore-password`
`gateway.tls.keystore.path`|OPTIONAL The path to the keystore file where the Gateway's TLS certificate and keypair are stored. If not set, the default keystore file will be used - data/security/keystores/gateway.jks.|null
`gateway.tls.keystore.type`|OPTIONAL The type of the keystore file where the Gateway's TLS certificate and keypair are stored. See `gateway.tls.keystore.path`.|`JKS`
`gateway.tls.key.alias`|OPTIONAL The alias for the Gateway's TLS certificate and keypair within the default keystore or the keystore specified via `gateway.tls.keystore.path`.|`gateway-identity`
`gateway.tls.key.passphrase.alias`|OPTIONAL The alias for passphrase for the Gateway's TLS private key stored within the default keystore or the keystore specified via `gateway.tls.keystore.path`.  NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the keystore password or the master secret will be used. See `gateway.tls.keystore.password.alias`|`gateway-identity-passphrase`
`gateway.signing.keystore.name`|OPTIONAL Filename of keystore file that contains the signing keypair. NOTE: An alias needs to be created using `knoxcli.sh create-alias` for the alias name `signing.key.passphrase` in order to provide the passphrase to access the keystore.|null
`gateway.signing.keystore.password.alias`|OPTIONAL Alias for the password to the keystore file holding the signing keypair. NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the master secret will be used.|`signing.keystore.password`
`gateway.signing.keystore.type`|OPTIONAL The type of the keystore file where the signing keypair is stored. See `gateway.signing.keystore.name`.|`JKS`
`gateway.signing.key.alias`|OPTIONAL alias for the signing keypair within the keystore specified via `gateway.signing.keystore.name`|null
`gateway.signing.key.passphrase.alias`|OPTIONAL The alias for passphrase for signing private key stored within the default keystore or the keystore specified via `gateway.signing.keystore.name`.  NOTE: An alias with the provided name should be created using `knoxcli.sh create-alias` inorder to provide the password; else the keystore password or the master secret will be used. See `gateway.signing.keystore.password.alias`|`signing.key.passphrase`
`ssl.enabled`|Indicates whether SSL is enabled for the Gateway|`true`
`ssl.include.ciphers`|A comma or pipe separated list of ciphers to accept for SSL. See the [JSSE Provider docs](http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider) for possible ciphers. These can also contain regular expressions as shown in the [Jetty documentation](http://www.eclipse.org/jetty/documentation/current/configuring-ssl.html).|all
`ssl.exclude.ciphers`|A comma or pipe separated list of ciphers to reject for SSL. See the [JSSE Provider docs](http://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html#SunJSSEProvider) for possible ciphers. These can also contain regular expressions as shown in the [Jetty documentation](http://www.eclipse.org/jetty/documentation/current/configuring-ssl.html).|none
`ssl.exclude.protocols`|Excludes a comma or pipe separated list of protocols to not accept for SSL or "none"|`SSLv3`
`gateway.remote.config.monitor.client`|A reference to the [remote configuration registry client](#Remote+Configuration+Registry+Clients) the remote configuration monitor will employ|null
`gateway.remote.config.monitor.client.allowUnauthenticatedReadAccess` | When a remote registry client is configured to access a registry securely, this property can be set to allow unauthenticated clients to continue to read the content from that registry by setting the ACLs accordingly. | `false`
`gateway.remote.config.registry.<name>`|A named [remote configuration registry client](#Remote+Configuration+Registry+Clients) definition, where _name_ is an arbitrary identifier for the connection|null
`gateway.cluster.config.monitor.ambari.enabled`| Indicates whether the Ambari cluster monitoring and associated dynamic topology updating is enabled | `false`
`gateway.cluster.config.monitor.ambari.interval` | The interval (in seconds) at which the Ambari cluster monitor will poll for cluster configuration changes | `60`
`gateway.cluster.config.monitor.cm.enabled`| Indicates whether the ClouderaManager cluster monitoring and associated dynamic topology updating is enabled | `false`
`gateway.cluster.config.monitor.cm.interval` | The interval (in seconds) at which the ClouderaManager cluster monitor will poll for cluster configuration changes | `60`
`gateway.remote.alias.service.enabled` | Turn on/off remote alias service | `true`
`gateway.read.only.override.topologies` | A comma-delimited list of topology names which should be forcibly treated as read-only. | none
`gateway.discovery.default.address` | The default discovery address, which is applied if no address is specified in a descriptor. | null
`gateway.discovery.default.cluster` | The default discovery cluster name, which is applied if no cluster name is specified in a descriptor. | null
`gateway.dispatch.whitelist` | A semicolon-delimited list of regular expressions for controlling to which endpoints Knox dispatches and redirects will be permitted. If `DEFAULT` is specified, or the property is omitted entirely, then a default domain-based whitelist will be derived from the Knox host. If `HTTPS_ONLY` is specified a default domain-based whitelist will be derived from the Knox host for only HTTPS urls. An empty value means no dispatches will be permitted. | null
`gateway.dispatch.whitelist.services` | A comma-delimited list of service roles to which the `gateway.dispatch.whitelist` will be applied. | none
`gateway.strict.topology.validation` | If true, topology XML files will be validated against the topology schema during redeploy | `false`
`gateway.topology.redeploy.requires.changes` | If `true`, XML topology redeployment will happen only if the topology content is different than the actually deployed one. That is, a simple `touch` command will not yield in topology redeployment in this case. | `false`
`gateway.global.rules.services` | Set the list of service names that have global rules, all services that are not in this list have rules that are treated as scoped to only to that service. | `"NAMENODE","JOBTRACKER", "WEBHDFS", "WEBHCAT", "OOZIE", "WEBHBASE", "HIVE", "RESOURCEMANAGER"`
`gateway.xforwarded.header.context.append.servicename` | Add service name to x-forward-context header for the defined list of services. | `LIVYSERVER`
`gateway.knox.token.exp.server-managed` | Default server-managed token state configuration for all KnoxToken service and JWT provider deployments | `false`
`gateway.knox.token.eviction.interval` | The period (seconds) about which the token state reaper will evict state for expired tokens. This configuration only applies when server-managed token state is enabled either in gateway-site or at the topology level. | `300` (5 minutes)
`gateway.knox.token.eviction.grace.period` | A duration (seconds) beyond a token's expiration to wait before evicting its state. This configuration only applies when server-managed token state is enabled either in gateway-site or at the topology level. | `86400` (24 hours)
`gateway.knox.token.permissive.validation` | When this feature is enabled and server managed state is enabled and Knox is presented with a valid token which is absent in server managed state, Knox will verify it without throwing an UnknownTokenException  | `false`
`gateway.jetty.max.form.content.size` | This optional parameter allows end-user to configure the form content in Knox's embedded Jetty server that a request can process is limited to protect from Denial of Service attacks. The size in bytes is limited by Jetty's ContextHandler#getMaxFormContentSize() or if there is no context then the "org.eclipse.jetty.server.Request.maxFormContentSize" attribute. | `200000`
`gateway.jetty.max.form.keys` | This optional parameter allows end-user to configure the number of parameters keys is limited by Knox's embedded Jetty's ContextHandler#getMaxFormKeys() or if there is no context then the `org.eclipse.jetty.server.Request.maxFormKeys` attribute. | `1000`
`gateway.strict.transport.enabled` | This parameter enables the HTTP Strict-Transport-Security response header globally for every response. The topology wide config with WebAppSec provider takes precedence if it is enabled. | `false`
`gateway.strict.transport.option` | This optional parameter specifies a particular value for the HTTP Strict-Transport-Security header in case the global config is enabled. | `max-age=31536000; includeSubDomains`
`gateway.server.append.classpath` | A `;` delimited list of paths that are appended to the gateway server's classpath. | null
`gateway.server.prepend.classpath` | A `;` delimited list of paths that are prepended to the gateway server's classpath. | null


#### Topology Descriptors ####

The topology descriptor files provide the gateway with per-cluster configuration information.
This includes configuration for both the providers within the gateway and the services within the Hadoop cluster.
These files are located in `{GATEWAY_HOME}/conf/topologies`.
The general outline of this document looks like this.

    <topology>
        <gateway>
            <provider>
            </provider>
        </gateway>
        <service>
        </service>
    </topology>

There are typically multiple `<provider>` and `<service>` elements.

/topology
: Defines the provider and configuration and service topology for a single Hadoop cluster.

/topology/gateway
: Groups all of the provider elements

/topology/gateway/provider
: Defines the configuration of a specific provider for the cluster.

/topology/service
: Defines the location of a specific Hadoop service within the Hadoop cluster.

##### Provider Configuration #####

Provider configuration is used to customize the behavior of a particular gateway feature.
The general outline of a provider element looks like this.

    <provider>
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
        <param>
            <name></name>
            <value></value>
        </param>
    </provider>

/topology/gateway/provider
: Groups information for a specific provider.

/topology/gateway/provider/role
: Defines the role of a particular provider.
There are a number of pre-defined roles used by out-of-the-box provider plugins for the gateway.
These roles are: authentication, identity-assertion, rewrite and hostmap

/topology/gateway/provider/name
: Defines the name of the provider for which this configuration applies.
There can be multiple provider implementations for a given role.
Specifying the name is used to identify which particular provider is being configured.
Typically each topology descriptor should contain only one provider for each role but there are exceptions.

/topology/gateway/provider/enabled
: Allows a particular provider to be enabled or disabled via `true` or `false` respectively.
When a provider is disabled any filters associated with that provider are excluded from the processing chain.

/topology/gateway/provider/param
: These elements are used to supply provider configuration.
There can be zero or more of these per provider.

/topology/gateway/provider/param/name
: The name of a parameter to pass to the provider.

/topology/gateway/provider/param/value
: The value of a parameter to pass to the provider.

##### Service Configuration #####

Service configuration is used to specify the location of services within the Hadoop cluster.
The general outline of a service element looks like this.

    <service>
        <role>WEBHDFS</role>
        <url>http://localhost:50070/webhdfs</url>
    </service>

/topology/service
: Provider information about a particular service within the Hadoop cluster.
Not all services are necessarily exposed as gateway endpoints.

/topology/service/role
: Identifies the role of this service.
Currently supported roles are: WEBHDFS, WEBHCAT, WEBHBASE, OOZIE, HIVE, NAMENODE, JOBTRACKER, RESOURCEMANAGER
Additional service roles can be supported via plugins. Note: The role names are case sensitive and must be upper case.

topology/service/url
: The URL identifying the location of a particular service within the Hadoop cluster.

#### Hostmap Provider ####

The purpose of the Hostmap provider is to handle situations where hosts are known by one name within the cluster and another name externally.
This frequently occurs when virtual machines are used and in particular when using cloud hosting services.
Currently, the Hostmap provider is configured as part of the topology file.
The basic structure is shown below.

    <topology>
        <gateway>
            ...
            <provider>
                <role>hostmap</role>
                <name>static</name>
                <enabled>true</enabled>
                <param><name>external-host-name</name><value>internal-host-name</value></param>
            </provider>
            ...
        </gateway>
        ...
    </topology>

This mapping is required because the Hadoop services running within the cluster are unaware that they are being accessed from outside the cluster.
Therefore URLs returned as part of REST API responses will typically contain internal host names.
Since clients outside the cluster will be unable to resolve those host name they must be mapped to external host names.

##### Hostmap Provider Example - EC2 #####

Consider an EC2 example where two VMs have been allocated.
Each VM has an external host name by which it can be accessed via the internet.
However the EC2 VM is unaware of this external host name and instead is configured with the internal host name.

    External HOSTNAMES:
    ec2-23-22-31-165.compute-1.amazonaws.com
    ec2-23-23-25-10.compute-1.amazonaws.com

    Internal HOSTNAMES:
    ip-10-118-99-172.ec2.internal
    ip-10-39-107-209.ec2.internal

The Hostmap configuration required to allow access external to the Hadoop cluster via the Apache Knox Gateway would be this:

    <topology>
        <gateway>
            ...
            <provider>
                <role>hostmap</role>
                <name>static</name>
                <enabled>true</enabled>
                <param>
                    <name>ec2-23-22-31-165.compute-1.amazonaws.com</name>
                    <value>ip-10-118-99-172.ec2.internal</value>
                </param>
                <param>
                    <name>ec2-23-23-25-10.compute-1.amazonaws.com</name>
                    <value>ip-10-39-107-209.ec2.internal</value>
                </param>
            </provider>
            ...
        </gateway>
        ...
    </topology>

##### Hostmap Provider Example - Sandbox #####

The Hortonworks Sandbox 2.x poses a different challenge for host name mapping.
This version of the Sandbox uses port mapping to make the Sandbox VM appear as though it is accessible via localhost.
However the Sandbox VM is internally configured to consider sandbox.hortonworks.com as the host name.
So from the perspective of a client accessing Sandbox the external host name is localhost.
The Hostmap configuration required to allow access to Sandbox from the host operating system is this.

    <topology>
        <gateway>
            ...
            <provider>
                <role>hostmap</role>
                <name>static</name>
                <enabled>true</enabled>
                <param>
                    <name>localhost</name>
                    <value>sandbox,sandbox.hortonworks.com</value>
                </param>
            </provider>
            ...
        </gateway>
        ...
    </topology>

##### Hostmap Provider Configuration #####

Details about each provider configuration element is enumerated below.

topology/gateway/provider/role
: The role for a Hostmap provider must always be `hostmap`.

topology/gateway/provider/name
: The Hostmap provider supplied out-of-the-box is selected via the name `static`.

topology/gateway/provider/enabled
: Host mapping can be enabled or disabled by providing `true` or `false`.

topology/gateway/provider/param
: Host mapping is configured by providing parameters for each external to internal mapping.

topology/gateway/provider/param/name
: The parameter names represent the external host names associated with the internal host names provided by the value element.
This can be a comma separated list of host names that all represent the same physical host.
When mapping from internal to external host name the first external host name in the list is used.

topology/gateway/provider/param/value
: The parameter values represent the internal host names associated with the external host names provider by the name element.
This can be a comma separated list of host names that all represent the same physical host.
When mapping from external to internal host names the first internal host name in the list is used.


#### Simplified Topology Descriptors ####

Simplified descriptors are a means to facilitate provider configuration sharing and service endpoint discovery. Rather than
editing an XML topology descriptor, it's possible to create a simpler YAML (or JSON) descriptor specifying the desired contents
of a topology, which will yield a full topology descriptor and deployment.

##### Externalized Provider Configurations #####

Sometimes, the same provider configuration is applied to multiple Knox topologies. With the provider configuration externalized
from the simple descriptors, a single configuration can be referenced by multiple topologies. This helps reduce the duplication
of configuration, and the need to update multiple configuration files when a policy change is required. Updating a provider
configuration will trigger an update to all those topologies that reference it.

The contents of externalized provider configuration details are identical to the contents of the gateway element from a full topology descriptor.
The only difference is that those details are defined in a separate JSON/YAML file in `{GATEWAY_HOME}/conf/shared-providers/`, which is then
referenced by one or more descriptors.

*Provider Configuration Example*

	{
	  "providers": [
	    {
	      "role": "authentication",
	      "name": "ShiroProvider",
	      "enabled": "true",
	      "params": {
	        "sessionTimeout": "30",
	        "main.ldapRealm": "org.apache.knox.gateway.shirorealm.KnoxLdapRealm",
	        "main.ldapContextFactory": "org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory",
	        "main.ldapRealm.contextFactory": "$ldapContextFactory",
	        "main.ldapRealm.userDnTemplate": "uid={0},ou=people,dc=hadoop,dc=apache,dc=org",
	        "main.ldapRealm.contextFactory.url": "ldap://localhost:33389",
	        "main.ldapRealm.contextFactory.authenticationMechanism": "simple",
	        "urls./**": "authcBasic"
	      }
	    },
	    {
	      "name": "static",
	      "role": "hostmape",
	      "enabled": "true",
	      "params": {
	        "localhost": "sandbox,sandbox.hortonworks.com"
	      }
	    }
	  ]
	}
	


###### Sharing HA Providers ######

HA Providers are a special concern with respect to sharing provider configuration because they include service-specific (and possibly cluster-specific) configuration.

This requires extra attention because the service configurations corresponding to the associated HA Provider configuration must contain the correct content to function properly.

For a shared provider configuration with an HA Provider service:

- If the referencing descriptor does not declare the corresponding service, then the HA Provider configuration is effectively ignored since the service isn't exposed by the topology.
- If a corresponding service is declared in the descriptor
    - If service endpoint discovery is employed, then Knox should populate the URLs correctly to support the HA behavior.
    - Otherwise, the URLs must be explicitly specified for that service in the descriptor.
- If the descriptor content is correct, but the cluster service is not configured for HA, then the HA behavior obviously won't work.

__*Apache ZooKeeper-based HA Provider Services*__

The HA Provider configuration for some services (e.g., [HiveServer2](#HiveServer2+HA), [Kafka](#Kafka+HA)) includes references to Apache ZooKeeper hosts (i.e., the ZooKeeper ensemble)
and namespaces. It's important to understand the relationship of that ensemble configuration to the topologies referencing it. These ZooKeeper details are often cluster-specific. If the ZooKeeper ensemble in the provider configuration is part of cluster *A*, then it's probably incorrect to reference it in a topology for cluster *B* since the Hadoop service endpoints will probably be the wrong ones. However, if multiple clusters are working with a common ZooKeeper ensemble, then sharing this provider configuration *may* be appropriate.

*It's always best to specify cluster-specific details in a descriptor rather than a provider configuration.*

All of the service attributes, which can be specified in the HaProvider, can also be specified as params in the corresponding service declaration in the descriptor. If an attribute is specified in both the service declaration and the HaProvider, then the service-level value __overrides__ the HaProvider-level value.

    "services": [
      {
        "name": "HIVE",
        "params": {
          "enabled": "true",
          "zookeeperEnsemble": "host1:2181,host2:2181,host3:2181",
		  "zookeeperNamespace" : "hiveserver2",
		  "maxRetryAttempts" : "100"
        }
      }
    ]

Note that Knox can dynamically determine these ZooKeeper ensemble details for *some* services; for others, they are static provider configuration details.
The services for which Knox can discover the cluster-specific ZooKeeper details include:

- YARN
- HIVE
- WEBHDFS
- WEBHBASE
- WEBHCAT
- OOZIE
- ATLAS
- ATLAS-API
- KAFKA

For a subset of these supported services, Knox can also determine whether ZooKeeper-based HA is enabled or not.
This means that the *enabled* attribute of the HA Provider configuration for these services may be set to __auto__, and Knox will determine whether or not it is enabled based on that service's configuration in the target cluster.

	{
	  "providers": [
	    {
	      "role": "ha",
	      "name": "HaProvider",
	      "enabled": "true",
	      "params": {
	        "WEBHDFS": "maxFailoverAttempts=3;failoverSleep=1000;maxRetryAttempts=3;retrySleep=1000;enabled=true",
	        "HIVE": "maxFailoverAttempts=10;failoverSleep=1000;maxRetryAttempts=5;retrySleep=1000;enabled=auto",
	        "YARN": "maxFailoverAttempts=5;failoverSleep=5000;maxRetryAttempts=3;retrySleep=1000;enabled=auto"
	      }
	    }
	  ]
	}

These services include:

- YARN
- HIVE
- ATLAS
- ATLAS-API


Be sure to pay extra attention when sharing HA Provider configuration across topologies.


##### Simplified Descriptor Files #####

Simplified descriptors allow service URLs to be defined explicitly, just like full topology descriptors. However, if URLs are
omitted for a service, Knox will attempt to discover that service's URLs from the Hadoop cluster. Currently, this behavior is
only supported for clusters managed by Apache Ambari or Cloudera Manager. In any case, the simplified descriptors are much more
concise than a full topology descriptor.

*Descriptor Properties*

Property | Description
------------|-----------
`discovery-type`|The discovery source type. (Currently, the only supported types are `AMBARI` and `ClouderaManager`).
`discovery-address`|The endpoint address for the discovery source.
`discovery-user`|The username with permission to access the discovery source. Optional, if the discovery type supports default credential aliases.
`discovery-pwd-alias`|The alias of the password for the user with permission to access the discovery source. Optional, if the discovery type supports default credential aliases.
`provider-config-ref`|A reference to a provider configuration in `{GATEWAY_HOME}/conf/shared-providers/`.
`cluster`|The name of the cluster from which the topology service endpoints should be determined.
`services`|The collection of services to be included in the topology.
`applications`|The collection of applications to be included in the topology.


Two file formats are supported for two distinct purposes.

- YAML is intended for the individual hand-editing a simplified descriptor because of its readability.
- JSON is intended to be used for [API](#Admin+API) interactions.

That being said, there is nothing preventing the hand-editing of files in the JSON format. However, the API will *not* accept YAML files as input.

*YAML Example* (based on the HDP Docker Sandbox)

    ---
    # Discovery source config
    discovery-type : AMBARI
    discovery-address : http://sandbox.hortonworks.com:8080

    # If this is not specified, the alias ambari.discovery.user is checked for a username
    discovery-user : maria_dev

    # If this is not specified, the default alias ambari.discovery.password is used
    discovery-pwd-alias : sandbox.discovery.password
    
    # Provider config reference, the contents of which will be included in the resulting topology descriptor
    provider-config-ref : sandbox-providers

    # The cluster for which the details should be discovered
    cluster: Sandbox

    # The services to declare in the resulting topology descriptor, whose URLs will be discovered (unless a value is specified)
    services:
        - name: NAMENODE
        - name: JOBTRACKER
        - name: WEBHDFS
        - name: WEBHCAT
        - name: OOZIE
        - name: WEBHBASE
        - name: HIVE
        - name: RESOURCEMANAGER
        - name: KNOXSSO
          params:
              knoxsso.cookie.secure.only: true
              knoxsso.token.ttl: 100000
        - name: AMBARI
          urls:
              - http://sandbox.hortonworks.com:8080
        - name: AMBARIUI
          urls:
              - http://sandbox.hortonworks.com:8080
        - name: AMBARIWS
          urls:
              - ws://sandbox.hortonworks.com:8080


*JSON Example* (based on the HDP Docker Sandbox)

    {
      "discovery-type":"AMBARI",
      "discovery-address":"http://sandbox.hortonworks.com:8080",
      "discovery-user":"maria_dev",
      "discovery-pwd-alias":"sandbox.discovery.password",
      "provider-config-ref":"sandbox-providers",
      "cluster":"Sandbox",
      "services":[
        {"name":"NAMENODE"},
        {"name":"JOBTRACKER"},
        {"name":"WEBHDFS"},
        {"name":"WEBHCAT"},
        {"name":"OOZIE"},
        {"name":"WEBHBASE"},
        {"name":"HIVE"},
        {"name":"RESOURCEMANAGER"},
        {"name":"KNOXSSO",
          "params":{
          "knoxsso.cookie.secure.only":"true",
          "knoxsso.token.ttl":"100000"
          }
        },
        {"name":"AMBARI", "urls":["http://sandbox.hortonworks.com:8080"]},
        {"name":"AMBARIUI", "urls":["http://sandbox.hortonworks.com:8080"],
        {"name":"AMBARIWS", "urls":["ws://sandbox.hortonworks.com:8080"]}
      ]
    }


Both of these examples illustrate the specification of credentials for the interaction with the discovery source. If no credentials are specified, then the default
aliases are queried (if default aliases are supported for that discovery type). Use of the default aliases is sufficient for scenarios where Knox will only discover
topology details from a single source.
For multiple discovery sources however, it's most likely that each will require different sets of credentials. The discovery-user and discovery-pwd-alias
properties exist for this purpose. Note that whether using the default credential aliases or specifying a custom password alias, these
[aliases must be defined](#Alias+creation) prior to any attempt to deploy a topology using a simplified descriptor.


##### Cluster Discovery Provider Extensions #####

To support the ability to dynamically discover the endpoints for services being proxied, Knox provides cluster discovery provider extensions for Apache Ambari and Cloudera Manager.
The Ambari support has been available since Knox 1.1.0, and limited support for Cloudera Manager has been added in Knox 1.3.0.

These extensions allow discovery sources of the respective types to be queried for cluster details used to generate topologies from [simplified descriptors](#Simplified+Descriptor+Files).
The extension to be employed is specified on a per-descriptor basis, using the `discovery-type` descriptor property.


##### Deployment Directories #####

Effecting topology changes is as simple as modifying files in two specific directories.

The `{GATEWAY_HOME}/conf/shared-providers/` directory is the location where Knox looks for provider configurations. This directory is monitored for changes, such
that modifying a provider configuration file therein will trigger updates to any referencing simplified descriptors in the `{GATEWAY_HOME}/conf/descriptors/` directory.
*Care should be taken when deleting these files if there are referencing descriptors; any subsequent modifications of referencing descriptors will fail when the deleted
provider configuration cannot be found. The references should all be modified before deleting the provider configuration.*

Likewise, the `{GATEWAY_HOME}/conf/descriptors/` directory is monitored for changes, such that adding or modifying a simplified descriptor file in this directory will
trigger the generation and deployment of a topology descriptor. Deleting a descriptor from this directory will conversely result in the removal of the previously-generated
topology descriptor, and the associated topology will be undeployed.

If the service details for a deployed (generated) topology are changed in the cluster, then the Knox topology can be updated by 'touch'ing the simplified descriptor. This
will trigger discovery and regeneration/redeployment of the topology descriptor.

Note that deleting a generated topology descriptor from `{GATEWAY_HOME}/conf/topologies/` is not sufficient for its removal. If the source descriptor is modified, or Knox
is restarted, the topology descriptor will be regenerated and deployed. Removing generated topology descriptors should be done by removing the associated simplified descriptor.
For the same reason, editing generated topology descriptors is strongly discouraged since they can be inadvertently overwritten.


Another means by which these topology changes can be effected is the [Admin API](#Admin+API).

##### Cloud Federation Configuration #####

Cloud Federation feature allows for a topology based federation from one Knox instance to another (from on-prem Knox instance to cloud knox instance).

##### Cluster Configuration Monitoring #####

Another benefit gained through the use of simplified topology descriptors, and the associated service discovery, is the ability to monitor clusters for configuration changes.
__This is currently only available for clusters managed by Ambari and ClouderaManager.__

The gateway can monitor cluster configurations, and respond to changes by dynamically regenerating and redeploying the affected topologies.
The following properties in gateway-site.xml can be used to control this behavior.

    <property>
        <name>gateway.cluster.config.monitor.ambari.enabled</name>
        <value>false</value>
        <description>Enable/disable Ambari cluster configuration monitoring.</description>
    </property>

    <property>
        <name>gateway.cluster.config.monitor.ambari.interval</name>
        <value>60</value>
        <description>The interval (in seconds) for polling Ambari for cluster configuration changes.</description>
    </property>

    <!-- Cloudera Manager specific configuration -->

    <property>
        <name>gateway.cluster.config.monitor.cm.enabled</name>
        <value>false</value>
        <description>Enable/disable Cloudera Manager cluster configuration monitoring.</description>
    </property>

    <property>
        <name>gateway.cluster.config.monitor.cm.interval</name>
        <value>60</value>
        <description>The interval (in seconds) for polling Cloudera Manager for cluster configuration changes.</description>
    </property>

    <property>
        <name>gateway.cloudera.manager.descriptors.monitor.interval</name>
        <value>-1</value>
        <description>The interval (in milliseconds) for monitoring Hadoop XML resources in "GATEWAY_HOME/data/descriptors" with `.hxr` file postfix (see details below). If this property is set to a non-positive integer, monitoring of Hadoop XML resources is disabled.</description>
    </property>

    <property>
        <name>gateway.cloudera.manager.advanced.service.discovery.config.monitor.interval</name>
        <value>-1</value>
        <description>The interval (in milliseconds) for monitoring GATEWAY_HOME/conf/auto-discovery-advanced-configuration.properties (if exists) and notifies any AdvancedServiceDiscoveryConfigChangeListener if the file is changed since the last time it was loaded. Advanced configuration processing in Knox's service discovery flow helps fine-tuned service-level enablement on the topology level (see details below). If this property is set to a non-positive integer, this feature is disabled.</description>
    </property>

    <property>
        <name>gateway.cloudera.manager.service.discovery.maximum.retry.attemps</name>
        <value>3</value>
        <description>The maximum number of attempts Knox will try to connect to the configured Cloudera Manager cluster if there was a connection error.</description>
    </property>

    <property>
        <name>gateway.cloudera.manager.service.discovery.repository.cache.entry.ttl</name>
        <value>60</value>
        <description>Upon a successful Cloudera Manager service discovery event, Knox maintains an in-memory cache (repository) of discovered data (cluster, service, and role configs). This property indicates the entry TTL of this cache. See KNOX-2680 for more details.</description>
    </property>

Since service discovery supports multiple Ambari or ClouderaManager instances as discovery sources, multiple instances can be monitored for cluster configuration changes.

For example, if the cluster monitor is enabled, deployment of the following simple descriptor would trigger monitoring of the *Sandbox* cluster managed by Ambari @ http://sandbox.hortonworks.com:8080

    ---
    discovery-address : http://sandbox.hortonworks.com:8080
    discovery-user : maria_dev
    discovery-pwd-alias : sandbox.discovery.password
    cluster: Sandbox
    provider-config-ref : sandbox-providers
    services:
        - name: NAMENODE
        - name: JOBTRACKER
        - name: WEBHDFS
        - name: WEBHCAT
        - name: OOZIE
        - name: WEBHBASE
        - name: HIVE
        - name: RESOURCEMANAGER

Another *Sandbox* cluster, managed by a __different__ Ambari instance, could simultaneously be monitored by the same gateway instance.

Now, topologies can be kept in sync with their respective target cluster configurations, without administrator intervention or service interruption.

##### Hadoop XML resource monitoring
As described in [KNOX-2160,](https://issues.apache.org/jira/browse/KNOX-2160) to support topology management in Cloudera Manager it's beneficial that Knox is able to process a descriptor that CM can generate natively. As of now, Cloudera Manager's CSD framework is capable of producing a file of its parameters in the following formats:
 - Hadoop XML
 - properties
 - gflags

As the `gateway-site.xml` uses the Hadoop XML format it's quite obvious that the first option is the one that fits Knox the most.
One XML type descriptor file may contain one or more Knox descriptors using the following structure:

 - the configuration `name` would indicate the descriptor (topology) name
 - the configuration `value` would list all properties of a Knox
   descriptor
	 - service discovery related information (type, address,
   cluster, user/password alias)
     - services
	     - name
	     - url
	     - version (optional)
	     - parameters (optional)
     - applications (optional)
	     - name
	     - parameters (optional)

A sample descriptor file would look like this:


    <configuration>
      <property>
        <name>topology1</name>
        <value>
            discoveryType=ClouderaManager;
            discoveryAddress=http://host:123;
            discoveryUser=user;
            discoveryPasswordAlias=alias;
            cluster=Cluster 1;
            providerConfigRef=topology1-provider;
            app:knoxauth:param1.name=param1.value;
            app:KNOX;
            HIVE:url=http://localhost:389;
            HIVE:version=1.0;
            HIVE:httpclient.connectionTimeout=5m;
            HIVE:httpclient.socketTimeout=100m
        </value>
      </property>
      <property>
        <name>topology2</name>
        <value>
            discoveryType=ClouderaManager;
            discoveryAddress=http://host:123;
            discoveryUser=user;
            discoveryPasswordAlias=alias;
            cluster=Cluster 1;
            providerConfigRef=topology2-provider;
            app:KNOX;
            HDFS.url=https://localhost:443;
            HDFS:httpclient.connectionTimeout=5m;
            HDFS:httpclient.socketTimeout=100m
         </value>
      </property>
    </configuration>

Workflow:

1.  this kind of descriptor should also be placed in Knox's descriptor directory using a `.hxr` file prefix (e.g. `cm-descriptors.hxr`)
2.  once it's added or modified Knox's Hadoop XML resource monitor should parse the XML and build one or more instance(s) of  `org.apache.knox.gateway.topology.simple.SimpleDescriptor`
3.  after the Java object(s) got created it (they) should be saved in the Knox descriptor directory in JSON format. As a result, the same monitor should parse the new/modified JSON descriptor(s) and re-deploys it (them) using the already existing mechanism

##### Advanced service discovery configuration monitoring

With the above-discussed Hadoop XML resource monitoring Knox is capable of processing a Hadoop XML configuration file and turn its content into Knox providers.

Advanced service discovery configuration monitor extends that feature in a way it supports for the following use cases that are also Cloudera Manager integration specific:

1.) Cloudera Manager reports if auto-discovery is enabled for each known services. That is, a list of boolean properties can be generated by CM indicating if `SERVICE_X` is enabled or not in the following form: `gateway.auto.discovery.enabled.SERVICE_NAME=[true|false]`

The new Hadoop XML configuration parser should take this information into account, and add a certain service into the generated Knox descriptor **iff** that service is explicitly enabled or there is no boolean flag within the CM generated properties with that service name (indicating an unknown - custom - service).

Additionally, a set of known (expected) topologies can be listed by CM. These topologies (Knox descriptors) should be populated with an enabled service (only with its name) even if that particular service was not listed in the new style Hadoop XML configuration file within the descriptor that matches any of the expected topology names.

2.) There are some services - mainly UI services - that are not working without some more required services in place (mainly their API counterpart). For instance: `RANGERUI` won't work properly if `RANGER` is not available.

Knox's Hadoop XML configuration parser was modified to exclude any service from the generated Knox descriptor unless
 - all required services are available (if any)
 - all required services are enabled (see the previous point)



##### Remote Configuration Monitor #####

In addition to monitoring local directories for provider configurations and simplified descriptors, the gateway similarly supports monitoring either ZooKeeper or an SQL database.

##### Zookeeper based monitor #####

This monitor depends on a [remote configuration registry client](#Remote+Configuration+Registry+Clients), and that client must be specified by setting the following property in gateway-site.xml

    <property>
        <name>gateway.service.remoteconfigurationmonitor.impl</name>
        <value>org.apache.knox.gateway.topology.monitor.db.ZkRemoteConfigurationMonitorService</value>
    </property>

    <property>
        <name>gateway.remote.config.monitor.client</name>
        <value>sandbox-zookeeper-client</value>
        <description>Remote configuration monitor client name.</description>
    </property>

This client identifier is a reference to a remote configuration registry client, as in this example (also defined in gateway-site.xml)

    <property>
        <name>gateway.remote.config.registry.sandbox-zookeeper-client</name>
        <value>type=ZooKeeper;address=localhost:2181</value>
        <description>ZooKeeper configuration registry client details.</description>
    </property>

_The actual name of the client (e.g., sandbox-zookeeper-client) is not important, except that the reference matches the name specified in the client definition._

With this configuration, the gateway will monitor the following znodes in the specified ZooKeeper instance

    /knox
        /config
            /shared-providers
            /descriptors

The creation of these znodes, and the population of their respective contents, is an activity __not__ currently managed by the gateway. However, the [KNOX CLI](#Knox+CLI) includes commands for managing the contents
of these znodes.

These znodes are treated similarly to the local *shared-providers* and *descriptors* directories described in [Deployment Directories](#Deployment+Directories).
When the monitor notices a change to these znodes, it will attempt to effect the same change locally.

If a provider configuration is added to the */knox/config/shared-providers* znode, the monitor will download the new configuration to the local shared-providers directory.
Likewise, if a descriptor is added to the */knox/config/descriptors* znode, the monitor will download the new descriptor to the local descriptors directory, which will
trigger an attempt to generate and deploy a corresponding topology.

Modifications to the contents of these znodes, will yield the same behavior as can be seen resulting from the corresponding local modification.

znode | action | result
------|--------|--------
`/knox/config/shared-providers` | add | Download the new file to the local shared-providers directory
`/knox/config/shared-providers` | modify | Download the new file to the local shared-providers directory; If there are any existing descriptor references, then topology will be regenerated and redeployed for those referencing descriptors.
`/knox/config/shared-providers` | delete | Delete the corresponding file from the local shared-providers directory
`/knox/config/descriptors` | add | Download the new file to the local descriptors directory; A corresponding topology will be generated and deployed.
`/knox/config/descriptors` | modify | Download the new file to the local descriptors directory; The corresponding topology will be regenerated and redeployed.
`/knox/config/descriptors` | delete | Delete the corresponding file from the local descriptors directory

This simplifies the configuration for HA gateway deployments, in that the gateway instances can all be configured to monitor the same ZooKeeper instance, and changes to the znodes' contents will be applied to all those gateway instances. With this approach, it is no longer necessary to manually deploy topologies to each of the gateway instances.

_A Note About ACLs_

    While the gateway does not currently require secure interactions with remote registries, it is recommended
    that ACLs be applied to restrict at least writing of the entries referenced by this monitor. If write
    access is available to everyone, then the contents of the configuration cannot be known to be trustworthy,
    and there is the potential for malicious activity. Be sure to carefully consider who will have the ability
    to define configuration in monitored remote registries and apply the necessary measures to ensure its
    trustworthiness.


#### Remote Configuration Registry Clients ####

One or more features of the gateway employ remote configuration registry (e.g., ZooKeeper) clients. These clients are configured by setting properties in the gateway configuration (`gateway-site.xml`).

Each client configuration is a single property, the name of which is prefixed with __gateway.remote.config.registry.__ and suffixed by the client identifier.
The value of such a property, is a registry-type-specific set of semicolon-delimited properties for that client, including the type of registry with which it will interact.

    <property>
        <name>gateway.remote.config.registry.a-zookeeper-client</name>
        <value>type=ZooKeeper;address=zkhost1:2181,zkhost2:2181,zkhost3:2181</value>
        <description>ZooKeeper configuration registry client details.</description>
    </property>

In the preceeding example, the client identifier is __a-zookeeper-client__, by way of the property name __gateway.remote.config.registry.a-zookeeper-client__.

The property value specifies that the client is intended to interact with ZooKeeper. It also specifies the particular ZooKeeper ensemble with which it will interact; this could be a single ZooKeeper instance as well.

The property value may also include an optional namespace, to which the client will be restricted (i.e., "chroot" the client).

    <property>
        <name>gateway.remote.config.registry.a-zookeeper-client</name>
        <value>type=ZooKeeper;address=zkhost1:2181,zkhost2:2181,zkhost3:2181;namespace=/knox/config</value>
        <description>ZooKeeper configuration registry client details.</description>
    </property>

At least for the ZooKeeper type, authentication details may also be specified as part of the property value, for interacting with instances for which authentication is required.

__Digest Authentication Example__

    <property>
        <name>gateway.remote.config.registry.a-zookeeper-client</name>
        <value>type=ZooKeeper;address=zkhost1:2181,zkhost2:2181,zkhost3:2181;authType=Digest;principal=myzkuser;credentialAlias=myzkpass</value>
        <description>ZooKeeper configuration registry client details.</description>
    </property>


__Kerberos Authentication Example__

    <property>
        <name>gateway.remote.config.registry.a-zookeeper-client</name>
        <value>type=ZooKeeper;address=zkhost1:2181,zkhost2:2181,zkhost3:2181;authType=Kerberos;principal=myzkuser;keytab=/home/user/myzk.keytab;useKeyTab=true;useTicketCache=false</value>
        <description>ZooKeeper configuration registry client details.</description>
    </property>


_While multiple such clients can be configured, for ZooKeeper clients, there is currently a limitation with respect to authentication. Multiple clients cannot each have distinct authentication configurations. This limitation is imposed by the underlying ZooKeeper client. Therefore, the clients must all be insecure (no authentication configured), or they must all authenticate to the same ZooKeeper using the same credentials._

The [remote configuration monitor](#Remote+Configuration+Monitor) facility uses these client configurations to perform its function.


##### SQL databse based monitor #####

The SQL based remote configuration monitor works like the Zookeeper monitor, but it monitors a relational database.

Enabling this monitor requires setting gateway.service.remoteconfigurationmonitor.impl in gateway-site.xml to org.apache.knox.gateway.topology.monitor.db.DbRemoteConfigurationMonitorService.

    <property>
      <name>gateway.service.remoteconfigurationmonitor.impl</name>
      <value>org.apache.knox.gateway.topology.monitor.db.DbRemoteConfigurationMonitorService</value>
    </property>

Valid database settings need to be configured in gateway-site.xml. See "Configuring the JDBC token state service" for more information about the database configuration.

    <property>
      <name>gateway.database.type</name>
      <value>mysql</value>
    </property>
    <property>
      <name>gateway.database.connection.url</name>
      <value>jdbc:mysql://localhost:3306/knox</value>
    </property>

With this configuration, the gateway will periodically check the content of the KNOX_PROVIDERS and KNOX_DESCRIPTORS tables and it will
modify (create, update or delete) the local files under the shared-providers and descriptors directories respectively.

 The interval (in seconds) at which the remote configuration monitor will poll the database is controlled by the following property.

    gateway.remote.config.monitor.db.poll.interval.seconds

The default value is 30 seconds.    


 * If a remote configuration exists in the datbase but doesn't exist on the file system, then the monitor is going to create the file with corresponding content.

 * If an existing remote configuration was deleted from the database (logical deletion) but it still exists on the local file system, then the monitor is going to delete the corresponding file. The logically deleted records are eventually cleared up by the monitor.

 * If a remote configuration exists in the database with a different content than the local file, then the monitor is going to update the
content of the local file with the content from the database. However to avoid unnecessary IO operations the monitor only updates the
content once and if there were no further changes in the database since this last update time, then it will skip changing the local content
until a new change happens in the database (indicated by the last_modified_time column). This means you can do temporary changes on the local file system without losing your modifications (until a change happens in the database).

The content of the database *must* be changed by the Knox Admin UI or the by the Admin API.

#### Remote Alias Service ####

Knox can be configured to use a remote alias service. The remote alias service is pluggable to support multiple different backends. The feature can be disabled by setting the property `gateway.remote.alias.service.enabled` to `false` in `gateway-site.xml`. Knox needs to be restarted for this change to take effect.

```
<property>
    <name>gateway.remote.alias.service.enabled</name>
    <value>false</value>
    <description>Turn on/off Remote Alias service (true by default)</description>
</property>
```

The type of remote alias service can be configured by default using `gateway.remote.alias.service.config.type`. If necessary the remote alias service config prefix can be changed with `gateway.remote.alias.service.config.prefix`. Changing the prefix affects all remote alias service configurations.
##### Remote Alias Service - HashiCorp Vault #####

The HashiCorp Vault remote alias service is deigned to store aliases into HashiCorp Vault. It is configured by setting `gateway.remote.alias.service.config.type` to `hashicorp.vault` in gateway-site.xml. The table below highlights configuration parameters for the HashiCorp Vault remote alias service. Knox needs to be restarted for this change to take effect.

Property    | Description
------------|------------
`gateway.remote.alias.service.config.hashicorp.vault.address`|Address of the HashiCorp Vault server
`gateway.remote.alias.service.config.hashicorp.vault.secrets.engine`|HashiCorp Vault secrets engine
`gateway.remote.alias.service.config.hashicorp.vault.path.prefix`|HashiCorp Vault secrets engine path prefix

There are multiple authentication mechanisms supported by HashiCorp Vault. Knox supports pluggable authentication mechanisms. The authentication type is configured by setting `gateway.remote.alias.service.config.hashicorp.vault.authentication.type` in gateway-site.xml.

__Token Authentication__

Token authentication takes a single setting `gateway.remote.alias.service.config.hashicorp.vault.authentication.token` and takes either the value of the authentication token or a local alias configured with `${ALIAS=token_name}`.

__Kubernetes Authentication__

Kubernetes authentication takes a single setting `gateway.remote.alias.service.config.hashicorp.vault.authentication.kubernetes.role` which defines the role to use when connecting to Vault. The Kubernetes authentication mechanism uses the secrets prepopulated into a K8S pod to authenticate to Vault. Knox can then use the secrets from Vault after being authenticated.

##### Remote Alias Service - Zookeeper #####

The Zookeeper remote alias service is designed to store aliases into Apache Zookeeper. It supports monitoring for remote aliases that are added, deleted or updated. The Zookeeper remote alias service is configured by turning the Remote Configuration Monitor on and setting `gateway.remote.alias.service.config.type` to `zookeeper` in gateway-site.xml. Knox needs to be restarted for this change to take effect. 

#### Logging ####

If necessary you can enable additional logging by editing the `log4j2.xml` file in the `conf` directory.
Changing the `rootLogger` value from `ERROR` to `DEBUG` will generate a large amount of debug logging.
A number of useful, more fine loggers are also provided in the file.

With the 2.0 release, Knox uses Log4j 2 instead of Log4j 1. Apache Log4j 2 is the successor of Log4j 1, but it is incompatible with its predecessor.
The main differene is that from now on Knox uses XML file format for configuring logging properties instead of `.property` files.

If you have an existing deployment with customized Log4j 1 `.property` files you will need to convert them to the new Log4j 2 format.

##### Launcher changes #####

The JVM property name that specifies the location of the Log4j configuration file, was changed to `log4j.configurationFile`.

For example

    -Dlog4j.configurationFile=gateway-log4j2.xml

Instead of
    
    -Dlog4j.configuration=conf/gateway-log4j.properties


##### Configuration file changes #####

Log4j 2 uses a different configuration file format than Log4j 1.

    app.log.dir=logs
    app.log.file=${launcher.name}.log
    log4j.rootLogger=ERROR, drfa
    log4j.appender.stdout=org.apache.log4j.ConsoleAppender
    log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
    log4j.appender.stdout.layout.ConversionPattern=%d{yy/MM/dd HH:mm:ss} %p %c{2}: %m%n
    log4j.appender.drfa=org.apache.log4j.DailyRollingFileAppender
    log4j.appender.drfa.File=${app.log.dir}/${app.log.file}
    log4j.appender.drfa.DatePattern=.yyyy-MM-dd
    log4j.appender.drfa.layout=org.apache.log4j.PatternLayout
    log4j.appender.drfa.layout.ConversionPattern=%d{ISO8601} %-5p %c{2} (%F:%M(%L)) - %m%n
    log4j.logger.org.apache.http.impl.conn=INFO
    log4j.logger.org.apache.http.impl.client=INFO
    log4j.logger.org.apache.http.client=INFO


For example the equivalent of the above Log4j 1 config file looks like this:

    <Configuration>
        <Properties>
            <Property name="app.log.dir">logs</Property>
            <Property name="app.log.file">${sys:launcher.name}.log</Property>
        </Properties>
        <Appenders>
            <Console name="stdout" target="SYSTEM_OUT">
                <PatternLayout pattern="%d{yy/MM/dd HH:mm:ss} %p %c{2}: %m%n" />
            </Console>
            <RollingFile name="drfa" fileName="${app.log.dir}/${app.log.file}" filePattern="${app.log.dir}/${app.log.file}.%d{yyyy-MM-dd}">
                <PatternLayout pattern="%d{ISO8601} %-5p %c{2} (%F:%M(%L)) - %m%n" />
                <TimeBasedTriggeringPolicy />
            </RollingFile>
        </Appenders>
        <Loggers>
            <Logger name="org.apache.http.impl.client" level="INFO" />
            <Logger name="org.apache.http.client" level="INFO" />
            <Logger name="org.apache.http.impl.conn" level="INFO" />
            <Root level="ERROR">
                <AppenderRef ref="drfa" />
            </Root>
        </Loggers>
    </Configuration>

Log levels can be set on individual Java packages similarly as before. The Loggers inherit properties like logging level and appender types from their ancestors.

##### Log4j Migration #####

If you have an existing Knox installation which uses the default Logging settings, with no customizations, then you can simply upgrade to the new version and overwrite the `*-log4j.property` files with the `*-log4j2.xml` files.


Old Log4j 1.x property files:

    -rw-r--r--  1 user  group  3880 Sep 10 10:49 gateway-log4j.properties
    -rw-r--r--  1 user  group  1481 Sep 10 10:49 knoxcli-log4j.properties
    -rw-r--r--  1 user  group  1493 Sep 10 10:49 ldap-log4j.properties
    -rw-r--r--  1 user  group  1436 Sep 10 10:49 shell-log4j.properties

The new Log4j 2 XML configuration files:

    -rw-r--r--  1 user  group  4619 Jan 22  2020 gateway-log4j2.xml
    -rw-r--r--  1 user  group  1684 Jan 22  2020 knoxcli-log4j2.xml
    -rw-r--r--  1 user  group  1765 Jan 22  2020 ldap-log4j2.xml
    -rw-r--r--  1 user  group  1621 Jan 22  2020 shell-log4j2.xml

If you have a lot of customizations in place, you will need to convert the property files to XML file format.

There is a third party script that helps you with the conversion:

    https://github.com/mulesoft-labs/log4j2-migrator

The final result is not always 100% correct, you might need to do some manual adjustments.

Usage:

    $ groovy log4j2migrator.groovy log4j.properties > log4j2.xml


The scripts uses ​​AsyncLoggers by default which requires the com.lmax:disruptor library which is not distributed with Knox by default. 

To avoid having this dependency you can replace all the `AsyncLogger` tags to `Logger`s.

     $ groovy log4j2migrator.groovy log4j.properties > log4j2.xml | sed 's/AsyncLogger/Logger/g'

Pay attention to the custom date time patterns in the configuration file. The script doesn't always convert them properly.

You can find more information about the Log4j 2 configuration file format at here: https://logging.apache.org/log4j/2.x/manual/configuration.html#XML


##### Custom Appenders and Layouts #####

Custom Appenders or Layouts based on the Log4j 1 API, are not going to work with Log4j 2. Those need to be rewritten using the new API.

The `AppenderSkeleton` class does not exist in Log4j 2, you should extend from `AbstractAppender` instead.

You can read more about extending Log4j 2 at: https://logging.apache.org/log4j/2.x/manual/extending.html


#### Java VM Options ####

TODO - Java VM options doc.


#### Persisting the Master Secret ####

The master secret is required to start the server.
This secret is used to access secured artifacts by the gateway instance.
By default, the keystores, trust stores, and credential stores are all protected with the master secret.
However, if a custom keystore is set, it and the contained keys may have different passwords. 

You may persist the master secret by supplying the *\-persist-master* switch at startup.
This will result in a warning indicating that persisting the secret is less secure than providing it at startup.
We do make some provisions in order to protect the persisted password.

It is encrypted with AES 128 bit encryption and where possible the file permissions are set to only be accessible by the user that the gateway is running as.

After persisting the secret, ensure that the file at `data/security/master` has the appropriate permissions set for your environment.
This is probably the most important layer of defense for master secret.
Do not assume that the encryption is sufficient protection.

A specific user should be created to run the gateway. This user will be the only user with permissions for the persisted master file.

See the Knox CLI section for descriptions of the command line utilities related to the master secret.

#### Management of Security Artifacts ####

There are a number of artifacts that are used by the gateway in ensuring the security of wire level communications, access to protected resources and the encryption of sensitive data.
These artifacts can be managed from outside of the gateway instances or generated and populated by the gateway instance itself.

The following is a description of how this is coordinated with both standalone (development, demo, etc.) gateway instances and instances as part of a cluster of gateways in mind.

Upon start of the gateway server:

1. Look for a credential store at `data/security/keystores/__gateway-credentials.jceks`.
   This credential store is used to store secrets/passwords that are used by the gateway.
   For instance, this is where the passphrase for accessing the gateway's TLS certificate is kept.
    * If the credential store is not found, then one is created and encrypted with the provided master secret.
    * If the credential store is found, then it is loaded using the provided master secret.
2. Look for an identity keystore at the configured location. If a configured location was not set, the 
   default location, `data/security/keystores/gateway.jks`, will be used. The identity keystore contains 
   the certificate and private key used to represent the identity of the server for TLS/SSL connections.   
    * If the identity keystore was not found, one is created in the configured or default location.  
      Then a self-signed certificate for use in standalone/demo mode is generated and stored with the 
      configured identity alias or the default alias of "gateway-identity".
    * If the identity keystore is found, then it is loaded using either the password found in the 
      credential store under the configured alias name or the provided master secret. It is tested 
      to ensure that a certificate and private key are found using the configured alias name or
      the default alias of "gateway-identity".
3. Look for a signing keystore at the configured location or the default location of `data/security/keystores/gateway.jks`.  
   The signing keystore contains the public and private key used for signature creation.
    * If the signing keystore was not found, the server will fail to start.  
    * If the signing keystore is found, then it is loaded using either the password found in the 
      credential store under the configured alias name or the provided master secret. It is tested 
      to ensure that a public and private key are found using the configured alias name or
      the default alias of "gateway-identity".

Upon deployment of a Hadoop cluster topology within the gateway we:

1. Look for a credential store for the topology. For instance, we have a sample topology that gets deployed out of the box.  We look for `data/security/keystores/sandbox-credentials.jceks`. This topology specific credential store is used for storing secrets/passwords that are used for encrypting sensitive data with topology specific keys.
    * If no credential store is found for the topology being deployed then one is created for it.
      Population of the aliases is delegated to the configured providers within the system that will require the use of a secret for a particular task.
      They may programmatically set the value of the secret or choose to have the value for the specified alias generated through the AliasService.
    * If a credential store is found then we ensure that it can be loaded with the provided master secret and the configured providers have the opportunity to ensure that the aliases are populated and if not to populate them.

By leveraging the algorithm described above we can provide a window of opportunity for management of these artifacts in a number of ways.

1. Using a single gateway instance as a master instance the artifacts can be generated or placed into the expected location and then replicated across all of the slave instances before startup.
2. Using an NFS mount as a central location for the artifacts would provide a single source of truth without the need to replicate them over the network. Of course, NFS mounts have their own challenges.
3. Using the KnoxCLI to create and manage the security artifacts.

See the Knox CLI section for descriptions of the command line utilities related to the security artifact management.

#### Keystores ####
In order to provide your own certificate for use by the Gateway, you may either

- provide your own keystore 
- import an existing key pair into the default Gateway keystore 
- generate a self-signed cert using the Java keytool

##### Providing your own keystore #####
A keystore in one of the following formats may be specified: 

- JKS: Java KeyStore file
- JCEKS: Java Cryptology Extension KeyStore file
- PKCS12: PKCS #12 file

See `gateway.tls.keystore.password.alias`, `gateway.tls.keystore.path`, `gateway.tls.keystore.type`, 
`gateway.tls.key.alias`, and `gateway.tls.key.passphrase.alias` under *Gateway Server Configuration*
for information on configuring the Gateway to use this keystore.

##### Importing a key pair into a Java keystore #####
One way to accomplish this is to start with a PKCS12 store for your key pair and then convert it to a Java keystore or JKS.

The following example uses OpenSSL to create a PKCS12 encoded store from your provided certificate and private key that are in PEM format.

    openssl pkcs12 -export -in cert.pem -inkey key.pem > server.p12

The next example converts the PKCS12 store into a Java keystore (JKS). It should prompt you for the 
keystore and key passwords for the destination keystore. You must use either the master-secret for the 
keystore password and key passphrase or choose you own.  If you choose your own passwords, you must 
use the Knox CLI utility to provide them to the Gateway.  

    keytool -importkeystore -srckeystore server.p12 -destkeystore gateway.jks -srcstoretype pkcs12

While using this approach a couple of important things to be aware of:

1. The alias MUST be properly set. If it is not the default value ("gateway-identity"), it must be 
   set in the configuration using `gateway.tls.key.alias`. You may need to change it using keytool 
   after the import of the PKCS12 store. You can use keytool to do this - for example:

        keytool -changealias -alias "1" -destalias "gateway-identity" -keystore gateway.jks -storepass {knoxpw}
    
2. The path to the identity keystore for the gateway MUST be the default path 
   (`{GATEWAY_HOME}/data/security/keystores/gateway.jks`) or MUST be specified in the configuration 
   using `gateway.tls.keystore.path` and `gateway.tls.keystore.type`  
3. The passwords for the keystore and the imported key may both be set to the master secret for the 
   gateway install.  If a custom password is used, the passwords must be imported into the credential 
   store using the Knox CLI `create-alias` command.  The aliases for the passwords must then be set 
   in the configuration using `gateway.tls.keystore.password.alias` and `gateway.tls.key.passphrase.alias`.  
   If both the keystore password and the key passphrase are the same, only `gateway.tls.keystore.password.alias` 
   needs to be set.  You can change the key passphrase after import using keytool. You may need to 
   do this in order to provision the password in the credential store as described later in this 
   section. For example:

        keytool -keypasswd -alias gateway-identity -keystore gateway.jks

The following will allow you to provision the password for the keystore or the passphrase for the 
private key that was set during keystore creation above - it will prompt you for the actual 
password/passphrase.

    bin/knoxcli.sh create-alias <alias name>

The default alias for the keystore password is `gateway-identity-keystore-password`, to use a different
alias, set `gateway.tls.keystore.password.alias` in the configuration. The default alias for the key 
passphrase is `gateway-identity-keystore-password`, to use a different alias, set 
`gateway.tls.key.passphrase.alias` in the configuration.

##### Generating a self-signed cert for use in testing or development environments #####

    keytool -genkey -keyalg RSA -alias gateway-identity -keystore gateway.jks \
        -storepass {master-secret} -validity 360 -keysize 2048

Keytool will prompt you for a number of elements used will comprise the distinguished name (DN) within your certificate. 

*NOTE:* When it prompts you for your First and Last name be sure to type in the hostname of the machine that your gateway instance will be running on. This is used by clients during hostname verification to ensure that the presented certificate matches the hostname that was used in the URL for the connection - so they need to match.

*NOTE:* When it prompts for the key password just press enter to ensure that it is the same as the keystore password. Which, as was described earlier, must match the master secret for the gateway instance. Alternatively, you can set it to another passphrase - take note of it and set the gateway-identity-passphrase alias to that passphrase using the Knox CLI.

See the Knox CLI section for descriptions of the command line utilities related to the management of the keystores.

##### Using a CA Signed Key Pair #####
For certain deployments a certificate key pair that is signed by a trusted certificate authority is required. There are a number of different ways in which these certificates are acquired and can be converted and imported into the Apache Knox keystore.

###### Option #1 ######
One way to do this is to install the certificate and keys in the default identity keystore and the 
master secret.  The following steps have been used to do this and are provided here for guidance in 
your installation.  You may have to adjust according to your environment.

1. Stop Knox gateway and back up all files in `{GATEWAY_HOME}/data/security/keystores`

        gateway.sh stop

2. Create a new master key for Knox and persist it. The master key will be referred to in following steps as `$master-key`

        knoxcli.sh create-master -force
        
3. Create identity keystore gateway.jks. cert in alias gateway-identity  

        cd {GATEWAY_HOME}/data/security/keystore  
        keytool -genkeypair -alias gateway-identity -keyalg RSA -keysize 1024 -dname "CN=$fqdn_knox,OU=hdp,O=sdge" -keypass $keypass -keystore gateway.jks -storepass $master-key -validity 300  

    NOTE: `$fqdn_knox` is the hostname of the Knox host. Some may choose `$keypass` to be the same as `$master-key`.

4. Create credential store to store the `$keypass` in step 3. This creates `__gateway-credentials.jceks` file

        knoxcli.sh create-alias gateway-identity-passphrase --value $keypass
        
5. Generate a certificate signing request from the gateway.jks

        keytool -keystore gateway.jks -storepass $master-key -alias gateway-identity -certreq -file knox.csr
        
6. Send the `knox.csr` file to the CA authority and get back the signed certificate (`knox.signed`). You also need the CA certificate, which normally can be requested through an openssl command or web browser or from the CA.

7. Import both the CA authority certificate (referred as `corporateCA.cer`) and the signed Knox certificate back into `gateway.jks`

        keytool -keystore gateway.jks -storepass $master-key -alias $hwhq -import -file corporateCA.cer  
        keytool -keystore gateway.jks -storepass $master-key -alias gateway-identity -import -file knox.signed  

    NOTE: Use any alias appropriate for the corporate CA.

8. Restart Knox gateway. Check `gateway.log` to check whether the gateway started properly and clusters are deployed. You can check the timestamp on cluster deployment files

        gateway.sh start
        ls -alrt {GATEWAY_HOME}/data/deployment

9. Verify that clients can use the CA authority cert to access Knox (which is the goal of using public signed cert) using curl or a web browser which has the CA certificate installed

        curl --cacert PATH_TO_CA_CERT -u tom:tom-password -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY

###### Option #2 ######
Another way to do this is to place the CA signed keypair in it's own keystore.  The creation of this
keystore is out of scope for this document. However, once created, the following steps can be use to
configure the Knox Gateway to use it. 

1. Move the new keystore into a location that the Knox Gateway can access.

2. Edit the gateway-site.xml file to set the configurations   
    * `gateway.tls.keystore.password.alias` - Alias of the password for the keystore
    * `gateway.tls.keystore.path` - Path to the keystore file
    * `gateway.tls.keystore.type` - Type of keystore file (JKS, JCEKS, PKCS12)
    * `gateway.tls.key.alias` - Alias for the certificate and key
    * `gateway.tls.key.passphrase.alias` - Alias of the passphrase for the key 
      (needed if the passphrase is different than the keystore password)

3. Provision the relevant passwords using the Knox CLI

        knoxcli.sh create-alias <alias> --value <password/passphrase>
        
4. Stop Knox gateway

        gateway.sh stop
       
5. Restart Knox gateway. Check `gateway.log` to check whether the gateway started properly and 
   clusters are deployed. You can check the timestamp on cluster deployment files

        gateway.sh start
        ls -alrt {GATEWAY_HOME}/data/deployment

6. Verify that clients can use the CA authority cert to access Knox (which is the goal of using 
   public signed cert) using curl or a web browser which has the CA certificate installed

        curl --cacert PATH_TO_CA_CERT -u tom:tom-password -X GET https://localhost:8443/gateway/sandbox/webhdfs/v1?op=GETHOMEDIRECTORY
  

##### Credential Store #####
Whenever you provide your own keystore with either a self-signed cert or an issued certificate signed 
by a trusted authority, you will need to set an alias for the keystore password and key passphrase. 
This is necessary for the current release in order for the system to determine the correct passwords 
to use for the keystore and the key.

The credential stores in Knox use the JCEKS keystore type as it allows for the storage of general secrets in addition to certificates.

Keytool may be used to create credential stores but the Knox CLI section details how to create aliases. 
These aliases are managed within credential stores which are created by the CLI as needed. The 
simplest approach is to create the relevant aliases with the Knox CLI. This will create the credential 
store if it does not already exist and add the password or passphrase.

See the Knox CLI section for descriptions of the command line utilities related to the management of the credential stores.

##### Provisioning of Keystores #####
Once you have created these keystores you must move them into place for the gateway to discover them and use them to represent its identity for SSL connections. This is done by copying the keystores to the `{GATEWAY_HOME}/data/security/keystores` directory for your gateway install.

#### Summary of Secrets to be Managed ####

1. Master secret - the same for all gateway instances in a cluster of gateways
2. All security related artifacts are protected with the master secret
3. Secrets used by the gateway itself are stored within the gateway credential store and are the same across all gateway instances in the cluster of gateways
4. Secrets used by providers within cluster topologies are stored in topology specific credential stores and are the same for the same topology across the cluster of gateway instances.
   However, they are specific to the topology - so secrets for one Hadoop cluster are different from those of another.
   This allows for fail-over from one gateway instance to another even when encryption is being used while not allowing the compromise of one encryption key to expose the data for all clusters.

NOTE: the SSL certificate will need special consideration depending on the type of certificate. Wildcard certs may be able to be shared across all gateway instances in a cluster.
When certs are dedicated to specific machines the gateway identity store will not be able to be blindly replicated as host name verification problems will ensue.
Obviously, trust-stores will need to be taken into account as well.
