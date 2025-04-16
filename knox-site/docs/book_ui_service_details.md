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

## UI Service Details ##

In the sections that follow, the integrations for proxying various UIs currently available out of the box with the
gateway will be described. These sections will include examples that demonstrate how to access each of these services
via the gateway.

These are the current Hadoop services with built-in support for their UIs.

* Name Node UI
* Job History UI
* Oozie UI
* HBase UI
* Yarn UI
* Spark UI
* Ambari UI
* Ranger Admin Console
* Atlas UI
* Zeppelin UI
* Nifi UI
* Hue UI

### Assumptions

This section assumes an environment setup similar to the one in the REST services section #[Service Details]

### Name Node UI ###

The Name Node UI is available on the same host and port combination that WebHDFS is available on. As mentioned in the
WebHDFS REST service configuration section, the values for the host and port can be obtained from the following
properties in hdfs-site.xml

    <property>
        <name>dfs.namenode.http-address</name>
        <value>sandbox.hortonworks.com:50070</value>
    </property>
    <property>
        <name>dfs.https.namenode.https-address</name>
        <value>sandbox.hortonworks.com:50470</value>
    </property>

The values above need to be reflected in each topology descriptor file deployed to the gateway.
The gateway by default includes a sample topology descriptor file `{GATEWAY_HOME}/deployments/sandbox.xml`.
The values in this sample are configured to work with an installed Sandbox VM.

    <service>
        <role>HDFSUI</role>
        <url>http://sandbox.hortonworks.com:50070</url>
    </service>

In addition to the service configuration for HDFSUI, the REST service configuration for WEBHDFS is also required.

    <service>
        <role>NAMENODE</role>
        <url>hdfs://sandbox.hortonworks.com:8020</url>
    </service>
    <service>
        <role>WEBHDFS</role>
        <url>http://sandbox.hortonworks.com:50070/webhdfs</url>
    </service>

By default the gateway is configured to use the HTTP endpoint for WebHDFS in the Sandbox.
This could alternatively be configured to use the HTTPS endpoint by providing the correct address.

#### Name Node UI URL Mapping ####

For Name Node UI URLs, the mapping of Knox Gateway accessible HDFS UI URLs to direct HDFS UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/hdfs` | `http://{webhdfs-host}:50070/` |

For example to browse the file system using the NameNode UI the URL in a web browser would be:

    http://sandbox.hortonworks.com:50070/explorer.html#

And using the gateway to access the same page the URL would be (where the gateway host:port is 'localhost:8443')

    https://localhost:8443/gateway/sandbox/hdfs/explorer.html#


### Job History UI ###

The Job History UI service can be configured in a topology by adding the following snippet. The values in this sample
are configured to work with an installed Sandbox VM.

    <service>
        <role>JOBHISTORYUI</role>
        <url>http://sandbox.hortonworks.com:19888</url>
    </service>

The values for the host and port can be obtained from the following property in mapred-site.xml

    <property>
        <name>mapreduce.jobhistory.webapp.address</name>
        <value>sandbox.hortonworks.com:19888</value>
    </property>

#### Job History UI URL Mapping ####

For Job History UI URLs, the mapping of Knox Gateway accessible Job History UI URLs to direct Job History UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/jobhistory` | `http://{jobhistory-host}:19888/jobhistory` |

### Oozie UI ###

The Oozie UI service can be configured in a topology by adding the following snippet. The values in this sample
are configured to work with an installed Sandbox VM.

    <service>
        <role>OOZIEUI</role>
        <url>http://sandbox.hortonworks.com:11000/oozie</url>
    </service>

The value for the URL can be obtained from the following property in oozie-site.xml

    <property>
        <name>oozie.base.url</name>
        <value>http://sandbox.hortonworks.com:11000/oozie</value>
    </property>

#### Oozie UI URL Mapping ####

For Oozie UI URLs, the mapping of Knox Gateway accessible Oozie UI URLs to direct Oozie UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/oozie/` | `http://{oozie-host}:11000/oozie/` |

### HBase UI ###

The HBase UI service can be configured in a topology by adding the following snippet. The values in this sample
are configured to work with an installed Sandbox VM.

    <service>
        <role>HBASEUI</role>
        <url>http://sandbox.hortonworks.com:16010</url>
    </service>

The values for the host and port can be obtained from the following property in hbase-site.xml.
Below the hostname of the HBase master is used since the bindAddress is 0.0.0.0

    <property>
        <name>hbase.master.info.bindAddress</name>
        <value>0.0.0.0</value>
    </property>
    <property>
        <name>hbase.master.info.port</name>
        <value>16010</value>
    </property>

#### HBase UI URL Mapping ####

For HBase UI URLs, the mapping of Knox Gateway accessible HBase UI URLs to direct HBase Master UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/hbase/webui/` | `http://{hbase-master-host}:16010/` |

### YARN UI ###

The YARN UI service can be configured in a topology by adding the following snippet. The values in this sample
are configured to work with an installed Sandbox VM.

    <service>
        <role>YARNUI</role>
        <url>http://sandbox.hortonworks.com:8088</url>
    </service>

The values for the host and port can be obtained from the following property in mapred-site.xml

    <property>
        <name>yarn.resourcemanager.webapp.address</name>
        <value>sandbox.hortonworks.com:8088</value>
    </property>

#### YARN UI URL Mapping ####

For Resource Manager UI URLs, the mapping of Knox Gateway accessible Resource Manager UI URLs to direct Resource Manager UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/yarn` | `http://{resource-manager-host}:8088/cluster` |

### Spark UI ###

The Spark History UI service can be configured in a topology by adding the following snippet. The values in this sample
are configured to work with an installed Sandbox VM.

    <service>
        <role>SPARKHISTORYUI</role>
        <url>http://sandbox.hortonworks.com:18080/</url>
    </service>

Please, note that for Spark versions older than 2.4.0 you need to set `spark.ui.proxyBase` to
`/{gateway-path}/{cluster-name}/sparkhistory` (for more information, please refer to SPARK-24209).

Moreover, before Spark 2.3.1, there is a bug is Spark which prevents the UI to work properly when the proxy is accessed
using a link which ends with "/" (SPARK-23644). So if the list of the applications is empty when you access the UI
though the gateway, please check that there is a "/" at the end of the URL you are using.

#### Spark History UI URL Mapping ####

For Spark History UI URLs, the mapping of Knox Gateway accessible Spark History UI URLs to direct Spark History UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/sparkhistory` | `http://{spark-history-host}:18080` |

### Ambari UI ###

Ambari UI has functionality around provisioning and managing services in a Hadoop cluster. This UI can now be used 
behind the Knox gateway.

To enable this functionality, a topology file needs to have the following configuration (for AMBARIUI and AMBARIWS):

    <service>
        <role>AMBARIUI</role>
        <url>http://<hostname>:<port></url>
    </service>

    <service>
        <role>AMBARIWS</role>
        <url>ws://<hostname>:<port></url>
    </service>

The default Ambari http port is 8080. Also, please note that the UI service also requires the Ambari REST API service and Ambari Websocket service
 to be enabled to function properly. An example of a more complete topology is given below.
 

#### Ambari UI URL Mapping ####

For Ambari UI URLs, the mapping of Knox Gateway accessible URLs to direct Ambari UI URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/ambari/` | `http://{ambari-host}:{ambari-port}/` |

#### Example Topology ####
 
The Ambari UI service may require a separate topology file due to its requirements around authentication. Knox passes
through authentication challenge and credentials to the service in this case.

    <topology>
        <gateway>
            <provider>
                <role>authentication</role>
                <name>Anonymous</name>
                <enabled>true</enabled>
            </provider>
            <provider>
                <role>identity-assertion</role>
                <name>Default</name>
                <enabled>false</enabled>
            </provider>
        </gateway>
        <service>
            <role>AMBARI</role>
            <url>http://localhost:8080</url>
        </service>
    
        <service>
            <role>AMBARIUI</role>
            <url>http://localhost:8080</url>
        </service>
        <service>
            <role>AMBARIWS</role>
            <url>ws://localhost:8080</url>
        </service>
    </topology>
    
Please look at JIRA issue [KNOX-705] for a known issue with this release.

### Ranger Admin Console ###

The Ranger Admin console can now be used behind the Knox gateway.

To enable this functionality, a topology file needs to have the following configuration:

    <service>
        <role>RANGERUI</role>
        <url>http://<hostname>:<port></url>
    </service>

The default Ranger http port is 8060. Also, please note that the UI service also requires the Ranger REST API service
 to be enabled to function properly. An example of a more complete topology is given below.
 

#### Ranger Admin Console URL Mapping ####

For Ranger Admin console URLs, the mapping of Knox Gateway accessible URLs to direct Ranger Admin console URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/ranger/` | `http://{ranger-host}:{ranger-port}/` |

#### Example Topology ####
 
The Ranger UI service may require a separate topology file due to its requirements around authentication. Knox passes
through authentication challenge and credentials to the service in this case.

    <topology>
        <gateway>
            <provider>
                <role>authentication</role>
                <name>Anonymous</name>
                <enabled>true</enabled>
            </provider>
            <provider>
                <role>identity-assertion</role>
                <name>Default</name>
                <enabled>false</enabled>
            </provider>
        </gateway>
        <service>
            <role>RANGER</role>
            <url>http://localhost:8060</url>
        </service>
    
        <service>
            <role>RANGERUI</role>
            <url>http://localhost:8060</url>
        </service>
    </topology>

### Atlas UI ###

### Atlas Rest API ###

The Atlas Rest API can now be used behind the Knox gateway.
To enable this functionality, a topology file needs to have the following configuration.

    <service>
        <role>ATLAS-API</role>
        <url>http://<ATLAS_HOST>:<ATLAS_PORT></url>
    </service>

The default Atlas http port is 21000. Also, please note that the UI service also requires the Atlas REST API
service to be enabled to function properly. An example of a more complete topology is given below.

#### Atlas Rest API URL Mapping ####

For Atlas Rest URLs, the mapping of Knox Gateway accessible URLs to direct Atlas Rest URLs is:

| Gateway | Cluster                          |
|---------|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{topology}/atlas/` | `http://{atlas-host}:{atlas-port}/` |

Access Atlas API using cULR call

     curl -i -k -L -u admin:admin -X GET \
               'https://knox-gateway:8443/gateway/{topology}/atlas/api/atlas/v2/types/typedefs?type=classification&_=1495442879421'

### Atlas UI ###

In addition to the Atlas REST API, from this release there is the ability to access some of the functionality via a web. The initial functionality is very limited and serves more as a starting point/placeholder. The details are below.

#### Atlas UI URL Mapping ####

The URL mapping for the Atlas UI is:

| Gateway                          |
|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{topology}/atlas/index.html` |

#### Example Topology for Atlas ####

    <topology>
        <gateway>
            <provider>
                <role>authentication</role>
                <name>Anonymous</name>
                <enabled>true</enabled>
            </provider>
            <provider>
                <role>identity-assertion</role>
                <name>Default</name>
                <enabled>false</enabled>
            </provider>
        </gateway>

        <service>
            <role>ATLAS-API</role>
            <url>http://<ATLAS_HOST>:<ATLAS_PORT></url>
        </service>

        <service>
            <role>ATLAS</role>
            <url>http://<ATLAS_HOST>:<ATLAS_PORT></url>
        </service>
    </topology>

Note: This feature will allow for 'anonymous' authentication. Essentially bypassing any LDAP or other authentication done by Knox and allow the proxied service to do the actual authentication.

### Zeppelin UI ###

Apache Knox can be used to proxy Zeppelin UI and also supports WebSocket protocol used by Zeppelin. 

#### Zeppelin UI URL Mapping ####

The URL mapping for the Zeppelin UI is:

| Gateway                          |
|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{topology}/zeppelin/` |

By default WebSocket functionality is disabled, it needs to be enabled for Zeppelin UI to work properly, it can be enabled by changing the `gateway.websocket.feature.enabled` property to 'true' in `<KNOX-HOME>/conf/gateway-site.xml` file, for e.g.

    <property>
        <name>gateway.websocket.feature.enabled</name>
        <value>true</value>
        <description>Enable/Disable websocket feature.</description>
    </property>

Example service definition for Zeppelin in topology file is as follows, note that both ZEPPELINWS and ZEPPELINUI service declarations are required.

    <service>
        <role>ZEPPELINWS</role>
        <url>ws://<ZEPPELIN_HOST>:<ZEPPELIN_PORT>/ws</url>
    </service>

    <service>
        <role>ZEPPELINUI</role>
        <url>http://<ZEPPELIN_HOST>:<ZEPPELIN_PORT></url>
    </service>

Knox also supports secure Zeppelin UIs, for secure UIs one needs to provision Zeppelin certificate into Knox truststore.  

### Nifi UI ###

You can use the Apache Knox Gateway to provide authentication access security for your NiFi services.

#### Nifi UI URL Mapping ####

The URL mapping for the NiFi UI is:

| Gateway                          |
|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{topology}/nifi-app/nifi/` |

The Gateway can be configured for Nifi by modifying the topology XML file.

In the topology XML file, add the following with the correct hostname and port:

    <service>
      <role>NIFI</role>
      <url><NIFI_HTTP_SCHEME>://<NIFI_HOST>:<NIFI_HTTP_SCHEME_PORT></url>
      <param name="useTwoWaySsl" value="true"/>
    </service>

Note the setting of the useTwoWaySsl param above. Nifi requires mutual authentication
via SSL and this param tells the dispatch to present a client cert to the server.

### Hue UI ###

You can use the Apache Knox Gateway to provide authentication access security for your Hue UI.

#### Hue UI URL Mapping ####

The URL mapping for the Hue UI is:

| Gateway                          |
|----------------------------------|
| `https://{gateway-host}:{gateway-port}/{gateway-path}/{topology}/hue/` |

The Gateway can be configured for Hue by modifying the topology XML file.

In the topology XML file, add the following with the correct hostname and port:

    <service>
      <role>HUE</role>
      <url><HUE_HTTP_SCHEME>://<HUE_HOST>:<HUE_HTTP_SCHEME_PORT></url>
    </service>

