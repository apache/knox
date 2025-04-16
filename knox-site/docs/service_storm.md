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

### Storm ###

Storm is a distributed realtime computation system. Storm exposes REST APIs for UI functionality that can be used for
retrieving metrics data and configuration information as well as management operations such as starting or stopping topologies.

The docs for this can be found here

https://github.com/apache/storm/blob/master/docs/STORM-UI-REST-API.md

To enable this functionality, a topology file needs to have the following configuration:

    <service>
        <role>STORM</role>
        <url>http://<hostname>:<port></url>
    </service>

The default UI daemon port is 8744. If it is configured to some other port, that configuration can be
found in `storm.yaml` as the value for the property `ui.port`.

In addition to the storm service configuration above, a STORM-LOGVIEWER service must be configured if the
log files are to be retrieved through Knox. The value of the port for the logviewer can be found by the property
`logviewer.port` also in the file `storm.yaml`.

    <service>
        <role>STORM-LOGVIEWER</role>
        <url>http://<hostname>:<port></url>
    </service>


#### Storm URL Mapping ####

For Storm URLs, the mapping of Knox Gateway accessible URLs to direct Storm URLs is the following.

| ------- | ------------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/storm` |
| Cluster | `http://{storm-host}:{storm-port}`                                      |

For the log viewer the mapping is as follows

| ------- | ------------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/storm/logviewer` |
| Cluster | `http://{storm-logviewer-host}:{storm-logviewer-port}`                                      |


#### Storm Examples

Some of the various calls that can be made and examples using curl are listed below.

    # 0. Getting cluster configuration
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/cluster/configuration'
    
    # 1. Getting cluster summary information
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/cluster/summary'

    # 2. Getting supervisor summary information
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/supervisor/summary'
    
    # 3. topologies summary information
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/topology/summary'
    
    # 4. Getting specific topology information. Substitute {id} with the topology id.
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/topology/{id}'

    # 5. To get component level information. Substitute {id} with the topology id and {component} with the component id e.g. 'spout'
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/storm/api/v1/topology/{id}/component/{component}'


The following POST operations all require a 'x-csrf-token' header along with other information that can be stored in a cookie file.
In particular the 'ring-session' header and 'JSESSIONID'.

    # 6. To activate a topology. Substitute {id} with the topology id and {token-value} with the x-csrf-token value.

    curl -ik -b ~/cookiejar.txt -c ~/cookiejar.txt -u guest:guest-password -H 'x-csrf-token:{token-value}' -X POST \
     http://localhost:8744/api/v1/topology/{id}/activate

    # 7. To de-activate a topology. Substitute {id} with the topology id and {token-value} with the x-csrf-token value.

    curl -ik -b ~/cookiejar.txt -c ~/cookiejar.txt -u guest:guest-password -H 'x-csrf-token:{token-value}' -X POST \
     http://localhost:8744/api/v1/topology/{id}/deactivate

    # 8. To rebalance a topology. Substitute {id} with the topology id and {token-value} with the x-csrf-token value.

    curl -ik -b ~/cookiejar.txt -c ~/cookiejar.txt -u guest:guest-password -H 'x-csrf-token:{token-value}' -X POST \
     http://localhost:8744/api/v1/topology/{id}/rebalance/0

    # 9. To kill a topology. Substitute {id} with the topology id and {token-value} with the x-csrf-token value.

    curl -ik -b ~/cookiejar.txt -c ~/cookiejar.txt -u guest:guest-password -H 'x-csrf-token:{token-value}' -X POST \
     http://localhost:8744/api/v1/topology/{id}/kill/0
