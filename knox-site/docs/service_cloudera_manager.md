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

### Cloudera Manager ###

Knox provides proxied access to Cloudera Manager API.

#### Gateway configuration ####

The Gateway can be configured for Cloudera Manager API by modifying the topology XML file
and providing a new service XML file.

In the topology XML file, add the following with the correct hostname:

    <service>
      <role>CM-API</role>
      <url>http://<cloudera-manager>:7180/api</url>
    </service>

