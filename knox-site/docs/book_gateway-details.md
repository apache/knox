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
-->

## Gateway Details ##

This section describes the details of the Knox Gateway itself. Including:

* How URLs are mapped between a gateway that services multiple Hadoop clusters and the clusters themselves
* How the gateway is configured through `gateway-site.xml` and cluster specific topology files
* How to configure the various policy enforcement provider features such as authentication, authorization, auditing, hostmapping, etc.

### URL Mapping ###

The gateway functions much like a reverse proxy.
As such, it maintains a mapping of URLs that are exposed externally by the gateway to URLs that are provided by the Hadoop cluster.

#### Default Topology URLs #####
In order to provide compatibility with the Hadoop Java client and existing CLI tools, the Knox Gateway has provided a feature called the _Default Topology_. This refers to a topology deployment that will be able to route URLs without the additional context that the gateway uses for differentiating from one Hadoop cluster to another. This allows the URLs to match those used by existing clients that may access WebHDFS through the Hadoop file system abstraction.

When a topology file is deployed with a file name that matches the configured default topology name, a specialized mapping for URLs is installed for that particular topology. This allows the URLs that are expected by the existing Hadoop CLIs for WebHDFS to be used in interacting with the specific Hadoop cluster that is represented by the default topology file.

The configuration for the default topology name is found in `gateway-site.xml` as a property called: `default.app.topology.name`.

The default value for this property is empty.


When deploying the `sandbox.xml` topology and setting `default.app.topology.name` to `sandbox`, both of the following example URLs work for the same underlying Hadoop cluster:

    https://{gateway-host}:{gateway-port}/webhdfs
    https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/webhdfs

These default topology URLs exist for all of the services in the topology.

#### Fully Qualified URLs #####
Examples of mappings for WebHDFS, WebHCat, Oozie and HBase are shown below.
These mapping are generated from the combination of the gateway configuration file (i.e. `{GATEWAY_HOME}/conf/gateway-site.xml`) and the cluster topology descriptors (e.g. `{GATEWAY_HOME}/conf/topologies/{cluster-name}.xml`).
The port numbers shown for the Cluster URLs represent the default ports for these services.
The actual port number may be different for a given cluster.

* WebHDFS
    * Gateway: `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/webhdfs`
    * Cluster: `http://{webhdfs-host}:50070/webhdfs`
* WebHCat (Templeton)
    * Gateway: `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/templeton`
    * Cluster: `http://{webhcat-host}:50111/templeton}`
* Oozie
    * Gateway: `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/oozie`
    * Cluster: `http://{oozie-host}:11000/oozie}`
* HBase
    * Gateway: `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/hbase`
    * Cluster: `http://{hbase-host}:8080`
* Hive JDBC
    * Gateway: `jdbc:hive2://{gateway-host}:{gateway-port}/;ssl=true;sslTrustStore={gateway-trust-store-path};trustStorePassword={gateway-trust-store-password};transportMode=http;httpPath={gateway-path}/{cluster-name}/hive`
    * Cluster: `http://{hive-host}:10001/cliservice`

The values for `{gateway-host}`, `{gateway-port}`, `{gateway-path}` are provided via the gateway configuration file (i.e. `{GATEWAY_HOME}/conf/gateway-site.xml`).

The value for `{cluster-name}` is derived from the file name of the cluster topology descriptor (e.g. `{GATEWAY_HOME}/deployments/{cluster-name}.xml`).

The value for `{webhdfs-host}`, `{webhcat-host}`, `{oozie-host}`, `{hbase-host}` and `{hive-host}` are provided via the cluster topology descriptor (e.g. `{GATEWAY_HOME}/conf/topologies/{cluster-name}.xml`).

Note: The ports 50070 (9870 for Hadoop 3.x), 50111, 11000, 8080 and 10001 are the defaults for WebHDFS, WebHCat, Oozie, HBase and Hive respectively.
Their values can also be provided via the cluster topology descriptor if your Hadoop cluster uses different ports.

Note: The HBase REST API uses port 8080 by default. This often clashes with other running services.
In the Hortonworks Sandbox, Apache Ambari might be running on this port, so you might have to change it to a different port (e.g. 60080).