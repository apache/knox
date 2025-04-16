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

<img src="../static/images/knox-logo.gif" align="left" alt="Knox"/>
<!-- <img src="/assets/images/apache-logo.gif" alt="Apache"/> -->
<img src="../static/images/apache-logo.gif" align="right" alt="Apache"/>

# Apache Knox Gateway 2.1.x User's Guide #


## Introduction ##

The Apache Knox Gateway is a system that provides a single point of authentication and access for Apache Hadoop services in a cluster.
The goal is to simplify Hadoop security for both users (i.e. who access the cluster data and execute jobs) and operators (i.e. who control access and manage the cluster).
The gateway runs as a server (or cluster of servers) that provide centralized access to one or more Hadoop clusters.
In general the goals of the gateway are as follows:

* Provide perimeter security for Hadoop REST APIs to make Hadoop security easier to setup and use
    * Provide authentication and token verification at the perimeter
    * Enable authentication integration with enterprise and cloud identity management systems
    * Provide service level authorization at the perimeter
* Expose a single URL hierarchy that aggregates REST APIs of a Hadoop cluster
    * Limit the network endpoints (and therefore firewall holes) required to access a Hadoop cluster
    * Hide the internal Hadoop cluster topology from potential attackers


## Export Controls ##

Apache Knox Gateway includes cryptographic software.
The country in which you currently reside may have restrictions on the import, possession, use, and/or
re-export to another country, of encryption software.
BEFORE using any encryption software, please check your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software, to see if this is permitted.
See http://www.wassenaar.org for more information.

The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS),
has classified this software as Export Commodity Control Number (ECCN) 5D002.C.1,
which includes information security software using or performing cryptographic functions with asymmetric algorithms.
The form and manner of this Apache Software Foundation distribution makes it eligible for export under the
License Exception ENC Technology Software Unrestricted (TSU) exception
(see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.

The following provides more details on the included cryptographic software:

* Apache Knox Gateway uses the ApacheDS which in turn uses Bouncy Castle generic encryption libraries.
* See http://www.bouncycastle.org for more details on Bouncy Castle.
* See http://directory.apache.org/apacheds for more details on ApacheDS.


<<../common/footer.md>>

