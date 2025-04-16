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

### Kafka ###

Knox provides gateway functionality to Kafka when used with the Confluent Kafka REST Proxy. The Kafka REST APIs allow the user to view the status 
of the cluster, perform administrative actions and produce messages.

<p>Note: Consumption of messages via Knox at this time is not supported.</p>  

The docs for the Confluent Kafka REST Proxy can be found here:
http://docs.confluent.io/current/kafka-rest/docs/index.html

To enable this functionality, a topology file needs to have the following configuration:

    <service>
        <role>KAFKA</role>
        <url>http://<kafka-rest-host>:<kafka-rest-port></url>
    </service>

The default Kafka REST Proxy port is 8082. If it is configured to some other port, that configuration can be found in 
`kafka-rest.properties` under the property `listeners`.

#### Kafka URL Mapping ####

For Kafka URLs, the mapping of Knox Gateway accessible URLs to direct Kafka URLs is the following.

| ------- | ------------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/kafka` |
| Cluster | `http://{kakfa-rest-host}:{kafka-rest-port}}`                               |


#### Kafka Examples via cURL

Some of the various calls that can be made and examples using curl are listed below.

    # 0. Getting topic info
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/kafka/topics'

    # 1. Publish message to topic
    
    curl -ikv -u guest:guest-password -X POST 'https://localhost:8443/gateway/sandbox/kafka/topics/TOPIC1' -H 'Content-Type: application/vnd.kafka.json.v2+json' -H 'Accept: application/vnd.kafka.v2+json' --data '"records":[{"value":{"foo":"bar"}}]}'

### Kafka HA ###

Knox provides basic failover functionality for calls made to Kafka. Since the Confluent Kafka REST Proxy does not register
itself with ZooKeeper, the HA component looks in ZooKeeper for instances of Kafka and then performs a light weight ping for
the presence of the REST Proxy on the same hosts. As such the Kafka REST Proxy must be installed on the same host as Kafka.
The user should not supply URLs in the service definition.  

Note: Users of Ambari must manually startup the Confluent Kafka REST Proxy.

To enable HA functionality for Kafka in Knox the following configuration has to be added to the topology file.

    <provider>
        <role>ha</role>
        <name>HaProvider</name>
        <enabled>true</enabled>
        <param>
            <name>KAFKA</name>
            <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true;zookeeperEnsemble=machine1:2181,machine2:2181,machine3:2181</value>
       </param>
    </provider>

The role and name of the provider above must be as shown. The name in the 'param' section must match that of the service
role name that is being configured for HA and the value in the 'param' section is the configuration for that particular
service in HA mode. In this case the name is 'KAFKA'.

The various configuration parameters are described below:

* maxFailoverAttempts -
This is the maximum number of times a failover will be attempted. The failover strategy at this time is very simplistic
in that the next URL in the list of URLs provided for the service is used and the one that failed is put at the bottom
of the list. If the list is exhausted and the maximum number of attempts is not reached then the first URL will be tried
again after the list is fetched again from Zookeeper (a refresh of the list is done at this point)

* failoverSleep -
The amount of time in millis that the process will wait or sleep before attempting to failover.

* enabled -
Flag to turn the particular service on or off for HA.

* zookeeperEnsemble -
A comma separated list of host names (or IP addresses) of the ZooKeeper hosts that consist of the ensemble that the Kafka
servers register their information with. 

And for the service configuration itself the URLs need NOT be added to the list. For example:

    <service>
        <role>KAFKA</role>
    </service>

Please note that there is no `<url>` tag specified here as the URLs for the Kafka servers are obtained from ZooKeeper.
