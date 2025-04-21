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

### Solr ###

Knox provides gateway functionality to Solr with support for versions 5.5+ and 6+. The Solr REST APIs allow the user to view the status 
of the collections, perform administrative actions and query collections.

See the Solr Quickstart (http://lucene.apache.org/solr/quickstart.html) section of the Solr documentation for examples of the Solr REST API.

Since Knox provides an abstraction over Solr and ZooKeeper, the use of the SolrJ CloudSolrClient is no longer supported.  You should replace 
instances of CloudSolrClient with HttpSolrClient.

<p>Note: Updates to Solr via Knox require a POST operation require the use of preemptive authentication which is not directly supported by the 
SolrJ API at this time.</p>  

To enable this functionality, a topology file needs to have the following configuration:

    <service>
        <role>SOLR</role>
        <version>6.0.0</version>
        <url>http://<solr-host>:<solr-port></url>
    </service>

The default Solr port is 8983. Adjust the version specified to either '5.5.0 or '6.0.0'.

For Solr 5.5.0 you also need to change the role name to `SOLRAPI` like this:

    <service>
        <role>SOLRAPI</role>
        <version>5.5.0</version>
        <url>http://<solr-host>:<solr-port></url>
    </service>


#### Solr URL Mapping ####

For Solr URLs, the mapping of Knox Gateway accessible URLs to direct Solr URLs is the following.

| ------- | ------------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/solr` |
| Cluster | `http://{solr-host}:{solr-port}/solr`                               |


#### Solr Examples via cURL

Some of the various calls that can be made and examples using curl are listed below.

    # 0. Query collection
    
    curl -ikv -u guest:guest-password -X GET 'https://localhost:8443/gateway/sandbox/solr/select?q=*:*&wt=json'

    # 1. Query cluster status
    
    curl -ikv -u guest:guest-password -X POST 'https://localhost:8443/gateway/sandbox/solr/admin/collections?action=CLUSTERSTATUS' 

### Solr HA ###

Knox provides basic failover functionality for calls made to Solr Cloud when more than one Solr instance is
installed in the cluster and registered with the same ZooKeeper ensemble. The HA functionality in this case fetches the
Solr URL information from a ZooKeeper ensemble, so the user need only supply the necessary ZooKeeper
configuration and not the Solr connection URLs.

To enable HA functionality for Solr Cloud in Knox the following configuration has to be added to the topology file.

    <provider>
        <role>ha</role>
        <name>HaProvider</name>
        <enabled>true</enabled>
        <param>
            <name>SOLR</name>
            <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true;zookeeperEnsemble=machine1:2181,machine2:2181,machine3:2181</value>
       </param>
    </provider>

The role and name of the provider above must be as shown. The name in the 'param' section must match that of the service
role name that is being configured for HA and the value in the 'param' section is the configuration for that particular
service in HA mode. In this case the name is 'SOLR'.

The various configuration parameters are described below:

* maxFailoverAttempts -
This is the maximum number of times a failover will be attempted. The failover strategy at this time is very simplistic
in that the next URL in the list of URLs provided for the service is used and the one that failed is put at the bottom
of the list. If the list is exhausted and the maximum number of attempts is not reached then the first URL will be tried
again after the list is fetched again from ZooKeeper (a refresh of the list is done at this point)

* failoverSleep -
The amount of time in millis that the process will wait or sleep before attempting to failover.

* enabled -
Flag to turn the particular service on or off for HA.

* zookeeperEnsemble -
A comma separated list of host names (or IP addresses) of the zookeeper hosts that consist of the ensemble that the Solr
servers register their information with. 

And for the service configuration itself the URLs need NOT be added to the list. For example.

    <service>
        <role>SOLR</role>
        <version>6.0.0</version>
    </service>

Please note that there is no `<url>` tag specified here as the URLs for the Solr servers are obtained from ZooKeeper.
