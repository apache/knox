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

### Default Service HA support ###

Knox provides connectivity based failover functionality for service calls that can be made to more than one server
instance in a cluster. To enable this functionality HaProvider configuration needs to be enabled for the service and
the service itself needs to be configured with more than one URL in the topology file.

The default HA functionality works on a simple round robin algorithm which can be configured to run in the following modes

* The top of the list of URLs is used to route all of a service's REST calls until a connection error occurs (Default).
* Round robin all the requests, distributing the load evenly across all the HA url's (`enableLoadBalancing`)
* Round robin with sticky session, requires cookies. Here, only new sessions will round robin ensuring sticky sessions. In case of failure next configured HA url is picked up to dispatch the request which might cause loss of session depending on the backend implementation (`enableStickySession`).
* Round robin with sticky session and no fallback, depends on `enableStickySession` to be true. Here, new sessions will round robin ensuring sticky sessions. In case of failure, Knox returns http status code 502. By default noFallback is turned off (`noFallback`).
* Turn off HA round robin feature for a request based on user-agent header property (`disableLoadBalancingForUserAgents`).

This goes on until the setting of 'maxFailoverAttempts' is reached.

At present the following services can use this default High Availability functionality and have been tested for the
same:

* WEBHCAT
* HBASE
* OOZIE
* HIVE

To enable HA functionality for a service in Knox the following configuration has to be added to the topology file.

    <provider>
         <role>ha</role>
         <name>HaProvider</name>
         <enabled>true</enabled>
         <param>
             <name>{SERVICE}</name>
             <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true;enableStickySession=true;</value>
         </param>
    </provider>

The role and name of the provider above must be as shown. The name in the 'param' section i.e. `{SERVICE}` must match
that of the service role name that is being configured for HA and the value in the 'param' section is the configuration
for that particular service in HA mode. For example, the value of `{SERVICE}` can be 'WEBHCAT', 'HBASE' or 'OOZIE'.

To configure multiple services in HA mode, additional 'param' sections can be added.

For example,

    <provider>
         <role>ha</role>
         <name>HaProvider</name>
         <enabled>true</enabled>
         <param>
             <name>OOZIE</name>
             <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true</value>
         </param>
         <param>
             <name>HBASE</name>
             <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true</value>
         </param>
         <param>
             <name>WEBHCAT</name>
             <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true</value>
         </param>
    </provider>

The various configuration parameters are described below:

* maxFailoverAttempts -
This is the maximum number of times a failover will be attempted. The failover strategy at this time is very simplistic
in that the next URL in the list of URLs provided for the service is used and the one that failed is put at the bottom
of the list. If the list is exhausted and the maximum number of attempts is not reached then the first URL will be tried
again.

* failoverSleep -
The amount of time in millis that the process will wait or sleep before attempting to failover.

* enabled -
Flag to turn the particular service on or off for HA.

* enableLoadBalancing -
Round robin all the requests, distributing the load evenly across all the HA url's (no sticky sessions)

* enableStickySession -
Round robin with sticky session. 

* noFallback -
Round robin with sticky session and no fallback, requires `enableStickySession` to be true.

* stickySessionCookieName -
Customize sticky session cookie name, default is 'KNOX_BACKEND-{serviceName}'.

And for the service configuration itself the additional URLs should be added to the list.

    <service>
        <role>{SERVICE}</role>
        <url>http://host1:port1</url>
        <url>http://host2:port2</url>
    </service>

For example,

    <service>
        <role>OOZIE</role>
        <url>http://sandbox1:11000/oozie</url>
        <url>http://sandbox2:11000/oozie</url>
    </service>

* disableLoadBalancingForUserAgents -
HA round robin feature can be turned off based on client user-agent header property. To turn it off for a specific user-agent add the user-agent value to parameter `disableLoadBalancingForUserAgents` which takes a list of user-agents (NOTE: user-agent value does not have to be an exact match, partial match will work). Default value is `ClouderaODBCDriverforApacheHive`

example:

    <provider>
         <role>ha</role>
         <name>HaProvider</name>
         <enabled>true</enabled>
         <param>
             <name>HIVE</name>
             <value>enableStickySession=true;enableLoadBalancing=true;enabled=true;disableLoadBalancingForUserAgents=Test User Agent, Test User Agent2,Test User Agent3 ,Test User Agent4 ;retrySleep=1000</value>
         </param>
    </provider>

