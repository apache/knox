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

### Common Service Config ###

It is possible to override a few of the global configuration settings provided in gateway-site.xml at the service level.
These overrides are specified as name/value pairs within the \<service> elements of a particular service.
The overridden settings apply only to that service.

The following table shows the common configuration settings available at the service level via service level parameters.
Individual services may support additional service level parameters.

Property | Description | Default
---------|-------------|---------
httpclient.maxConnections    | The maximum number of connections that a single httpclient will maintain to a single host:port. | 32
httpclient.connectionTimeout | The amount of time to wait when attempting a connection. The natural unit is milliseconds, but a 's' or 'm' suffix may be used for seconds or minutes respectively. The default timeout is system dependent. | 20s
httpclient.socketTimeout     | The amount of time to wait for data on a socket before aborting the connection. The natural unit is milliseconds, but a 's' or 'm' suffix may be used for seconds or minutes respectively. The default timeout is system dependent but is likely to be indefinite. | 20s

The example below demonstrates how these service level parameters are used.

    <service>
         <role>HIVE</role>
         <param>
             <name>httpclient.socketTimeout</name>
             <value>180s</value>
         </param>
    </service>
