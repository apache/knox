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

### Livy Server ###

Knox provides proxied access to Livy server for submitting Spark jobs.
The gateway can be used to provide authentication and encryption for clients to
servers like Livy.

#### Gateway configuration ####

The Gateway can be configured for Livy by modifying the topology XML file
and providing a new service XML file.

In the topology XML file, add the following with the correct hostname:

    <service>
      <role>LIVYSERVER</role>
      <url>http://<livy-server>:8998</url>
    </service>

Livy server will use proxyUser to run the Spark session. To avoid that a user can 
provide here any user (e.g. a more privileged), Knox will need to rewrite the 
JSON body to replace what so ever is the value of proxyUser is with the username of
the authenticated user.

    {  
      "driverMemory":"2G",
      "executorCores":4,
      "executorMemory":"8G",
      "proxyUser":"bernhard",
      "conf":{  
        "spark.master":"yarn-cluster",
        "spark.jars.packages":"com.databricks:spark-csv_2.10:1.5.0"
      }
    } 

The above is an example request body to be used to create a Spark session via Livy server and illustrates the "proxyUser" that requires rewrite.
