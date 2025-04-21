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

## Limitations ##


### Secure Oozie POST/PUT Request Payload Size Restriction ###

With one exception there are no known size limits for requests or responses payloads that pass through the gateway.
The exception involves POST or PUT request payload sizes for Oozie in a Kerberos secured Hadoop cluster.
In this one case there is currently a 4Kb payload size limit for the first request made to the Hadoop cluster.
This is a result of how the gateway negotiates a trust relationship between itself and the cluster via SPNEGO.
There is an undocumented configuration setting to modify this limit's value if required.
In the future this will be made more easily configurable and at that time it will be documented.

### Group Membership Propagation ###

Groups that are acquired via Shiro Group Lookup and/or Identity Assertion Group Principal Mapping are not propagated to the Hadoop services.
Therefore, groups used for Service Level Authorization policy may not match those acquired within the cluster via GroupMappingServiceProvider plugins.

### Knox Consumer Restriction ###

Consumption of messages via Knox at this time is not supported.  The Confluent Kafka REST Proxy that Knox relies upon is stateful when used for
consumption of messages.

