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


### Service Test API

The gateway supports a Service Test API that can be used to test Knox's ability to connect to each of the different Hadoop services via a simple HTTP GET request. To be able to access this API, one must add the following lines into the topology for which you wish to run the service test.

    <service>
      <role>SERVICE-TEST</role>
    </service>

After adding the above to a topology, you can make a cURL request with the following structure

    curl -i -k "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/service-test?username=guest&password=guest-password"

An alternate method of providing credentials:

    curl -i -k -u guest:guest-password https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/service-test

Below is an example response. The gateway is also capable of returning XML if specified in the request's "Accept" HTTP header.

    {
        "serviceTestWrapper": {
         "Tests": {
          "ServiceTest": [
           {
            "serviceName": "WEBHDFS",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/webhdfs/v1/?op=LISTSTATUS",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHCAT",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/templeton/v1/status",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHCAT",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/templeton/v1/version",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHCAT",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/templeton/v1/version/hive",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHCAT",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/templeton/v1/version/hadoop",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "OOZIE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/oozie/v1/admin/build-version",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "OOZIE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/oozie/v1/admin/status",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "OOZIE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/oozie/versions",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHBASE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/hbase/version",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHBASE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/hbase/version/cluster",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHBASE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/hbase/status/cluster",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "WEBHBASE",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/hbase",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "RESOURCEMANAGER",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/resourcemanager/v1/{topology-name}/info",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "RESOURCEMANAGER",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/resourcemanager/v1/{topology-name}/metrics",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "RESOURCEMANAGER",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/resourcemanager/v1/{topology-name}/apps",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "FALCON",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/falcon/api/admin/stack",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "FALCON",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/falcon/api/admin/version",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "FALCON",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/falcon/api/metadata/lineage/serialize",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "FALCON",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/falcon/api/metadata/lineage/vertices/all",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "FALCON",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/falcon/api/metadata/lineage/edges/all",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "STORM",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/storm/api/v1/cluster/configuration",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "STORM",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/storm/api/v1/cluster/summary",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "STORM",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/storm/api/v1/supervisor/summary",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           },
           {
            "serviceName": "STORM",
            "requestURL": "http://{gateway-host}:{gateway-port}/gateway/{topology-name}/storm/api/v1/topology/summary",
            "responseContent": "Content-Length:0,Content-Type: application/json;charset=utf-8",
            "httpCode": 200,
            "message": "Request sucessful."
           }
          ]
         },
         "messages": {
          "message": [

          ]
         }
        }
    }


We can see that this service-test makes HTTP requests to each of the services through Knox using the specified topology. The test will only make calls to those services that have entries within the topology file.

##### Adding and Changing test URLs

URLs for each service are stored in `{GATEWAY_HOME}/data/services/{service-name}/{service-version}/service.xml`. Each `<testURL>` element represents a service resource that will be tested if the service is set up in the topology. You can add or remove these from the `service.xml` file. Just note if you add URLs there is no guarantee in the order they will be tested. All default URLs have been tested and work on various clusters. If a new URL is added and doesn't respond in a way the user expects then it is up to the user to determine whether the URL is correct or not.

##### Some important things to note:
 - In the first cURL request, the quotes are necessary around the URL or else a command line terminal will not include the `&password` query parameter in the request.
 - This API call does not require any credentials to receive a response from Knox, but expect to receive 401 responses from each of the services if none are provided.
