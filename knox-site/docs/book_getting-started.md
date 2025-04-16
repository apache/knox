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

## Apache Knox Details ##

This section provides everything you need to know to get the Knox gateway up and running against a Hadoop cluster.

#### Hadoop ####

An existing Hadoop 2.x or 3.x cluster is required for Knox to sit in front of and protect.
It is possible to use a Hadoop cluster deployed on EC2 but this will require additional configuration not covered here.
It is also possible to protect access to a services of a Hadoop cluster that is secured with Kerberos.
This too requires additional configuration that is described in other sections of this guide.
See #[Supported Services] for details on what is supported for this release.

The instructions that follow assume a few things:

1. The gateway is *not* collocated with the Hadoop clusters themselves.
2. The host names and IP addresses of the cluster services are accessible by the gateway where ever it happens to be running.

All of the instructions and samples provided here are tailored and tested to work "out of the box" against a [Hortonworks Sandbox 2.x VM][sandbox].


#### Apache Knox Directory Layout ####

Knox can be installed by expanding the zip/archive file.

The table below provides a brief explanation of the important files and directories within `{GATEWAY_HOME}`

| Directory                | Purpose |
| ------------------------ | ------- |
| conf/                    | Contains configuration files that apply to the gateway globally (i.e. not cluster specific ). |
| data/                    | Contains security and topology specific artifacts that require read/write access at runtime |
| conf/topologies/         | Contains topology files that represent Hadoop clusters which the gateway uses to deploy cluster proxies |
| data/security/           | Contains the persisted master secret and keystore dir |
| data/security/keystores/ | Contains the gateway identity keystore and credential stores for the gateway and each deployed cluster topology |
| data/services            | Contains service behavior definitions for the services currently supported. |
| bin/                     | Contains the executable shell scripts, batch files and JARs for clients and servers. |
| data/deployments/        | Contains deployed cluster topologies used to protect access to specific Hadoop clusters. |
| lib/                     | Contains the JARs for all the components that make up the gateway. |
| dep/                     | Contains the JARs for all of the components upon which the gateway depends. |
| ext/                     | A directory where user supplied extension JARs can be placed to extends the gateways functionality. |
| pids/                    | Contains the process ids for running LDAP and gateway servers |
| samples/                 | Contains a number of samples that can be used to explore the functionality of the gateway. |
| templates/               | Contains default configuration files that can be copied and customized. |
| README                   | Provides basic information about the Apache Knox Gateway. |
| ISSUES                   | Describes significant know issues. |
| CHANGES                  | Enumerates the changes between releases. |
| LICENSE                  | Documents the license under which this software is provided. |
| NOTICE                   | Documents required attribution notices for included dependencies. |


### Supported Services ###

This table enumerates the versions of various Hadoop services that have been tested to work with the Knox Gateway.

| Service                | Version     | Non-Secure  | Secure | HA |
| -----------------------|-------------|-------------|--------|----|
| WebHDFS                | 2.4.0       | ![y]        | ![y]   |![y]|
| WebHCat/Templeton      | 0.13.0      | ![y]        | ![y]   |![y]|
| Oozie                  | 4.0.0       | ![y]        | ![y]   |![y]|
| HBase                  | 0.98.0      | ![y]        | ![y]   |![y]|
| Hive (via WebHCat)     | 0.13.0      | ![y]        | ![y]   |![y]|
| Hive (via JDBC/ODBC)   | 0.13.0      | ![y]        | ![y]   |![y]|
| Yarn ResourceManager   | 2.5.0       | ![y]        | ![y]   |![n]|
| Kafka (via REST Proxy) | 0.10.0      | ![y]        | ![y]   |![y]|
| Storm                  | 0.9.3       | ![y]        | ![n]   |![n]|
| Solr                   | 5.5+ and 6+ | ![y]        | ![y]   |![y]|


### More Examples ###

These examples provide more detail about how to access various Apache Hadoop services via the Apache Knox Gateway.

* #[WebHDFS Examples]
* #[WebHCat Examples]
* #[Oozie Examples]
* #[HBase Examples]
* #[Hive Examples]
* #[Yarn Examples]
* #[Storm Examples]
