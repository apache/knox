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

### High Availability ###

This describes how Knox itself can be made highly available.

All Knox instances must be configured to use the same topology credential keystores.
These files are located under `{GATEWAY_HOME}/data/security/keystores/{TOPOLOGY_NAME}-credentials.jceks`.
They are generated after the first topology deployment.

In addition to these topology-specific credentials, gateway credentials and topologies must also be kept in-sync for Knox to operate in an HA manner.

#### Manually Synchronize Knox Instances ####

Here are the steps to manually sync topology credential keystores:

1. Choose a Knox instance that will be the source for topology credential keystores. Let's call it _keystores master_
2. Replace the topology credential keystores in the other Knox instances with topology credential keystores from the _keystores master_
3. Restart Knox instances

Manually synchronizing the gateway credentials and topologies involves using ssh/scp to copy the topology-related files to all the participating Knox instances, and running the Knox CLI on each participating instance to define the gateway credential aliases.

This manual process can be tedious and error-prone. As such, [ZooKeeper-based HA](#High+Availability+with+Apache+ZooKeeper) is recommended to simplify the management of these deployments.

#### High Availability with Apache ZooKeeper ####

Rather than manually keeping Knox HA instances in sync (in terms of credentials and topology), Knox can get it's state from Apache ZooKeeper.
By configuring all the Knox instances to monitor the same ZooKeeper ensemble, they can be kept in-sync by modifying the topology-related
configuration and/or credential aliases at only one of the instances (using the Admin UI, Admin API, or Knox CLI).

##### What is Automatically Synchronized Across Instances?

* Provider Configurations
* Descriptors
* *Topologies* (generated only)
* Credential Aliases

When a provider configuration or descriptor is added or updated to the ZooKeeper ensemble, all of the participating Knox instances will get the change, and the affected topologies will be [re]generated and [re]deployed. Similarly, if one of these is deleted, the affected topologies will be deleted and undeployed.

When provider configurations and descriptors are added, modified or removed using the Admin UI or API (when the Knox instance is configured to monitor a ZooKeeper ensemble), then those changes will be automatically reflected in the associated ZooKeeper ensemble. Those changes will subsequently be consumed by all the other Knox instances monitoring that ensemble.
By using the Admin UI or API, ssh/scp access to the Knox hosts can be avoided completely for the purpose of effecting topology changes.

Similarly, when the Knox CLI is used to create or delete a gateway alias (when the Knox instance is configured to monitor a ZooKeeper ensemble), that alias change is reflected in the ZooKeeper ensemble, and all other Knox instances montoring that ensemble will apply the change.


##### What is NOT Automatically Synchronized Across Instances?

* Topologies (XML)
* Gateway config (e.g., gateway-site, gateway-logging, etc...)

If you're creating/modifying topology XML files directly, then there is no automated support for keeping these in sync across Knox HA instances.

However, if the Knox instances are running in an Apache Ambari-managed cluster, there is limited support for keeping topology XML files and gateway configuration synchronized across those instances.

<br>

#### High Availability with Apache HTTP Server + mod_proxy + mod_proxy_balancer ####

##### 1 - Requirements #####

###### openssl-devel ######

openssl-devel is required for Apache Module mod_ssl.

    sudo yum install openssl-devel

###### Apache HTTP Server ######

Apache HTTP Server 2.4.6 or later is required. See this document for installing and setting up Apache HTTP Server: http://httpd.apache.org/docs/2.4/install.html

Hint: pass `--enable-ssl` to the `./configure` command to enable the generation of the Apache Module _mod_ssl_.

###### Apache Module mod_proxy ######

See this document for setting up Apache Module mod_proxy: http://httpd.apache.org/docs/2.4/mod/mod_proxy.html

###### Apache Module mod_proxy_balancer ######

See this document for setting up Apache Module mod_proxy_balancer: http://httpd.apache.org/docs/2.4/mod/mod_proxy_balancer.html

###### Apache Module mod_ssl ######

See this document for setting up Apache Module mod_ssl: http://httpd.apache.org/docs/2.4/mod/mod_ssl.html

##### 2 - Configuration example #####

###### Generate certificate for Apache HTTP Server ######

See this document for an example: http://www.akadia.com/services/ssh_test_certificate.html

By convention, Apache HTTP Server and Knox certificates are put into the `/etc/apache2/ssl/` folder.

###### Update Apache HTTP Server configuration file ######

This file is located under {APACHE_HOME}/conf/httpd.conf.

Following directives have to be added or uncommented in the configuration file:

* LoadModule proxy_module modules/mod_proxy.so
* LoadModule proxy_http_module modules/mod_proxy_http.so
* LoadModule proxy_balancer_module modules/mod_proxy_balancer.so
* LoadModule ssl_module modules/mod_ssl.so
* LoadModule lbmethod_byrequests_module modules/mod_lbmethod_byrequests.so
* LoadModule lbmethod_bytraffic_module modules/mod_lbmethod_bytraffic.so
* LoadModule lbmethod_bybusyness_module modules/mod_lbmethod_bybusyness.so
* LoadModule lbmethod_heartbeat_module modules/mod_lbmethod_heartbeat.so
* LoadModule slotmem_shm_module modules/mod_slotmem_shm.so

Also following lines have to be added to file. Replace placeholders (${...}) with real data:

    Listen 443
    <VirtualHost *:443>
       SSLEngine On
       SSLProxyEngine On
       SSLCertificateFile ${PATH_TO_CERTIFICATE_FILE}
       SSLCertificateKeyFile ${PATH_TO_CERTIFICATE_KEY_FILE}
       SSLProxyCACertificateFile ${PATH_TO_PROXY_CA_CERTIFICATE_FILE}

       ProxyRequests Off
       ProxyPreserveHost Off

       RequestHeader set X-Forwarded-Port "443"
       Header add Set-Cookie "ROUTEID=.%{BALANCER_WORKER_ROUTE}e; path=/" env=BALANCER_ROUTE_CHANGED
       <Proxy balancer://mycluster>
         BalancerMember ${HOST_#1} route=1
         BalancerMember ${HOST_#2} route=2
         ...
         BalancerMember ${HOST_#N} route=N

         ProxySet failontimeout=On lbmethod=${LB_METHOD} stickysession=ROUTEID 
       </Proxy>

       ProxyPass / balancer://mycluster/
       ProxyPassReverse / balancer://mycluster/
    </VirtualHost>

Note:

* SSLProxyEngine enables SSL between Apache HTTP Server and Knox instances;
* SSLCertificateFile and SSLCertificateKeyFile have to point to certificate data of Apache HTTP Server. User will use this certificate for communications with Apache HTTP Server;
* SSLProxyCACertificateFile has to point to Knox certificates.

###### Start/stop Apache HTTP Server ######

    APACHE_HOME/bin/apachectl -k start
    APACHE_HOME/bin/apachectl -k stop

###### Verify ######

Use Knox samples.
