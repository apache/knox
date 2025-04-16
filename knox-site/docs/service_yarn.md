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

### Yarn ###

Knox provides gateway functionality for the REST APIs of the ResourceManager. The ResourceManager REST APIs allow the
user to get information about the cluster - status on the cluster, metrics on the cluster, scheduler information,
information about nodes in the cluster, and information about applications on the cluster. Also as of Hadoop version
2.5.0, the user can submit a new application as well as kill it (or get state) using the 'Writable' APIs.

The docs for this can be found here

http://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/ResourceManagerRest.html

To enable this functionality, a topology file needs to have the following configuration:

    <service>
        <role>RESOURCEMANAGER</role>
        <url>http://<hostname>:<port>/ws</url>
    </service>

The default resource manager http port is 8088. If it is configured to some other port, that configuration can be
found in `yarn-site.xml` under the property `yarn.resourcemanager.webapp.address`.

#### Yarn URL Mapping ####

For Yarn URLs, the mapping of Knox Gateway accessible URLs to direct Yarn URLs is the following.

| ------- | ------------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/resourcemanager` |
| Cluster | `http://{yarn-host}:{yarn-port}/ws}`                                      |


#### Yarn Examples via cURL

Some of the various calls that can be made and examples using curl are listed below.

    # 0. Getting cluster info
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster'
    
    # 1. Getting cluster metrics
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/metrics'
    
    To get the same information in an xml format
    
    curl -ikv -u guest:guest-password -H Accept:application/xml -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/metrics'
    
    # 2. Getting scheduler information
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/scheduler'
    
    # 3. Getting all the applications listed and their information
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps'
    
    # 4. Getting applications statistics
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/appstatistics'
    
    Also query params can be used as below to filter the results
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/appstatistics?states=accepted,running,finished&applicationTypes=mapreduce'
    
    # 5. To get a specific application (please note, replace the application id with a real value)
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/{application_id}'
    
    # 6. To get the attempts made for a particular application
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/{application_id}/appattempts'
    
    # 7. To get information about the various nodes
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/nodes'
    
    Also to get a specific node, use an id obtained in the response from above (the node id is scrambled) and issue the following
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/nodes/{node_id}'
    
    # 8. To create a new Application
    
    curl -ikv -u guest:guest-password -X POST 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/new-application'
    
    An application id is returned from the request above and this can be used to submit an application.
    
    # 9. To submit an application, put together a request containing the application id received in the above response (please refer to Yarn REST
    API documentation).
    
    curl -ikv -u guest:guest-password -T request.json -H Content-Type:application/json -X POST 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps'
    
    Here the request is saved in a file called request.json
    
    #10. To get application state
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/{application_id}/state'
    
    curl -ikv -u guest:guest-password -H Content-Type:application/json -X PUT -T state-killed.json 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/application_1409008107556_0007/state'
    
    # 11. To kill an application that is running issue the below command with the application id of the application that is to be killed.
    The contents of the state-killed.json file are :
    
    {
      "state":"KILLED"
    }
    
    
    curl -ikv -u guest:guest-password -H Content-Type:application/json -X PUT -T state-killed.json 'https://localhost:8443/gateway/sandbox/resourcemanager/v1/cluster/apps/{application_id}/state'

Note: The sensitive data like nodeHttpAddress, nodeHostName, id will be hidden.

