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

### Gateway Samples ###

The purpose of the samples within the `{GATEWAY_HOME}/samples` directory is to demonstrate the capabilities of the Apache Knox Gateway to provide access to the numerous APIs that are available from the service components of a Hadoop cluster.

Depending on exactly how your Knox installation was done, there will be some number of steps required in order fully install and configure the samples for use.

This section will help describe the assumptions of the samples and the steps to get them to work in a couple of different deployment scenarios.

#### Assumptions of the Samples ####

The samples were initially written with the intent of working out of the box for the various Hadoop demo environments that are deployed as a single node cluster inside of a VM. The following assumptions were made from that context and should be understood in order to get the samples to work in other deployment scenarios:

* That there is a valid java JDK on the PATH for executing the samples
* The Knox Demo LDAP server is running on localhost and port 33389 which is the default port for the ApacheDS LDAP server.
* That the LDAP directory in use has a set of demo users provisioned with the convention of username and username"-password" as the password. Most of the samples have some variation of this pattern with "guest" and "guest-password".
* That the Knox Gateway instance is running on the same machine which you will be running the samples from - therefore "localhost" and that the default port of "8443" is being used.
* Finally, that there is a properly provisioned sandbox.xml topology in the `{GATEWAY_HOME}/conf/topologies` directory that is configured to point to the actual host and ports of running service components.

#### Steps for Demo Single Node Clusters ####

There should be little to do if anything in a demo environment that has been provisioned with illustrating the use of Apache Knox.

However, the following items will be worth ensuring before you start:

1. The `sandbox.xml` topology is configured properly for the deployed services
2. That there is a LDAP server running with guest/guest-password user available in the directory

#### Steps for Ambari deployed Knox Gateway ####

Apache Knox instances that are under the management of Ambari are generally assumed not to be demo instances. These instances are in place to facilitate development, testing or production Hadoop clusters.

The Knox samples can however be made to work with Ambari managed Knox instances with a few steps:

1. You need to have SSH access to the environment in order for the localhost assumption within the samples to be valid
2. The Knox Demo LDAP Server is started - you can start it from Ambari
3. The `default.xml` topology file can be copied to `sandbox.xml` in order to satisfy the topology name assumption in the samples
4. Be sure to use an actual Java JRE to run the sample with something like:

    /usr/jdk64/jdk1.7.0_67/bin/java -jar bin/shell.jar samples/ExampleWebHdfsLs.groovy

#### Steps for a manually installed Knox Gateway ####

For manually installed Knox instances, there is really no way for the installer to know how to configure the topology file for you.

Essentially, these steps are identical to the Ambari deployed instance except that #3 should be replaced with the configuration of the out of the box `sandbox.xml` to point the configuration at the proper hosts and ports.

1. You need to have SSH access to the environment in order for the localhost assumption within the samples to be valid.
2. The Knox Demo LDAP Server is started - you can start it from Ambari
3. Change the hosts and ports within the `{GATEWAY_HOME}/conf/topologies/sandbox.xml` to reflect your actual cluster service locations.
4. Be sure to use an actual Java JRE to run the sample with something like:

    /usr/jdk64/jdk1.7.0_67/bin/java -jar bin/shell.jar samples/ExampleWebHdfsLs.groovy
