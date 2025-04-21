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

### Elasticsearch ###

Elasticsearch provides a REST API for communicating with Elasticsearch via JSON over HTTP. Elasticsearch uses X-Pack to do its own security (authentication and authorization). Therefore, the Knox Gateway is to forward the user credentials to Elasticsearch, and treats the Elasticsearch-authenticated user as "anonymous" to the backend service via a doas query param while Knox will authenticate to backend services as itself.

#### Gateway configuration ####

The Gateway can be configured for Elasticsearch by modifying the topology XML file and providing a new service XML file.

In the topology XML file, add the following new service named "ELASTICSEARCH" with the correct elasticsearch-rest-server hostname and port number (e.g., 9200):

     <service>
       <role>ELASTICSEARCH</role>
       <url>http://<elasticsearch-rest-server>:9200/</url>
       <name>elasticsearch</name>
     </service>

#### Elasticsearch via Knox Gateway ####

After adding the above to a topology, you can make a cURL request similar to the following structures:

##### 1.  Elasticsearch Node Root Query #####

    curl -i -k -u username:password -H "Accept: application/json"  -X GET  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch"

    or

    curl -i -k -u username:password -H "Accept: application/json"  -X GET  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/"

The quotation marks around the URL, can be single quotes or double quotes on both sides, and can also be omitted (Note: This is true for all other Elasticsearch queries via Knox). Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 16:36:34 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 356
     Server: Jetty(9.2.15.v20160210)
     
     {"name":"w0A80p0","cluster_name":"elasticsearch","cluster_uuid":"poU7j48pSpu5qQONr64HLQ","version":{"number":"6.2.4","build_hash":"ccec39f","build_date":"2018-04-12T20:37:28.497551Z","build_snapshot":false,"lucene_version":"7.2.1","minimum_wire_compatibility_version":"5.6.0","minimum_index_compatibility_version":"5.0.0"},"tagline":"You Know, for Search"}
    
##### 2.  Elasticsearch Index - Creation, Deletion, Refreshing and Data Operations - Writing, Updating and Retrieval #####

###### (1) Index Creation ######

    curl -i -k -u username:password -H "Content-Type: application/json"  -X PUT  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}"  -d '{
    "settings" : {
        "index" : {
            "number_of_shards" : {index-shards-number},
            "number_of_replicas" : {index-replicas-number}
        }
      }
    }'

Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 16:51:31 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 65
     Server: Jetty(9.2.15.v20160210)
     
     {"acknowledged":true,"shards_acknowledged":true,"index":"estest"}

###### (2) Index Data Writing ######

For adding a "Hello Joe Smith" document:

    curl -i -k -u username:password -H "Content-Type: application/json"  -X PUT  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}/{document-type-name}/{document-id}"  -d '{
        "title":"Hello Joe Smith" 
    }'

Below is an example response:

     HTTP/1.1 201 Created
     Date: Wed, 23 May 2018 17:00:17 GMT
     Location: /estest/greeting/1
     Content-Type: application/json; charset=UTF-8
     Content-Length: 158
     Server: Jetty(9.2.15.v20160210)
     
     {"_index":"estest","_type":"greeting","_id":"1","_version":1,"result":"created","_shards":{"total":1,"successful":1,"failed":0},"_seq_no":0,"_primary_term":1}

###### (3) Index Refreshing ######

    curl -i -k -u username:password  -X POST  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}/_refresh" 

Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 17:02:32 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 49
     Server: Jetty(9.2.15.v20160210)
     
     {"_shards":{"total":1,"successful":1,"failed":0}}

###### (4) Index Data Upgrading ######

For changing the Person Joe Smith to Tom Smith:

    curl -i -k -u username:password -H "Content-Type: application/json"  -X PUT  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}/{document-type-name}/{document-id}"  -d '{ 
    "title":"Hello Tom Smith" 
    }'

Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 17:09:59 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 158
     Server: Jetty(9.2.15.v20160210)
     
     {"_index":"estest","_type":"greeting","_id":"1","_version":2,"result":"updated","_shards":{"total":1,"successful":1,"failed":0},"_seq_no":1,"_primary_term":1}

###### (5) Index Data Retrieval or Search ######

For finding documents with "title":"Hello" in a specified document-type:

    curl -i -k -u username:password -H "Accept: application/json" -X GET  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}/{document-type-name}/ _search?pretty=true;q=title:Hello"

Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 17:13:08 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 244
     Server: Jetty(9.2.15.v20160210)
     
     {"took":0,"timed_out":false,"_shards":{"total":1,"successful":1,"skipped":0,"failed":0},"hits":{"total":1,"max_score":0.2876821,"hits":[{"_index":"estest","_type":"greeting","_id":"1","_score":0.2876821,"_source":{"title":"Hello Tom Smith"}}]}}

###### (6) Index Deleting ######

    curl -i -k -u username:password  -X DELETE  "https://{gateway-hostname}:{gateway-port}/gateway/{topology-name}/elasticsearch/{index-name}"

Below is an example response:

     HTTP/1.1 200 OK
     Date: Wed, 23 May 2018 17:20:19 GMT
     Content-Type: application/json; charset=UTF-8
     Content-Length: 21
     Server: Jetty(9.2.15.v20160210)
     
     {"acknowledged":true}

