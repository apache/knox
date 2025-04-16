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

### HBase ###

HBase provides an optional REST API (previously called Stargate).
See the HBase REST Setup section below for getting started with the HBase REST API and Knox with the Hortonworks Sandbox environment.

The gateway by default includes a sample topology descriptor file `{GATEWAY_HOME}/deployments/sandbox.xml`.  The value in this sample is configured to work with an installed Sandbox VM.

    <service>
        <role>WEBHBASE</role>
        <url>http://localhost:60080</url>
        <param>
            <name>replayBufferSize</name>
            <value>8</value>
        </param>
    </service>

By default the gateway is configured to use port 60080 for Hbase in the Sandbox.  Please see the steps to configure the port mapping below.

A default replayBufferSize of 8KB is shown in the sample topology file above.  This may need to be increased if your query size is larger.

#### HBase URL Mapping ####

| ------- | ----------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/hbase` |
| Cluster | `http://{hbase-rest-host}:8080/`                                         |

#### HBase Examples ####

The examples below illustrate the set of basic operations with HBase instance using the REST API.
Use following link to get more details about HBase REST API: http://hbase.apache.org/book.html#_rest.

Note: Some HBase examples may not work due to enabled [Access Control](http://hbase.apache.org/book.html#_securing_access_to_your_data). User may not be granted access for performing operations in the samples. In order to check if Access Control is configured in the HBase instance verify `hbase-site.xml` for a presence of `org.apache.hadoop.hbase.security.access.AccessController` in `hbase.coprocessor.master.classes` and `hbase.coprocessor.region.classes` properties.  
To grant the Read, Write, Create permissions to `guest` user execute the following command:

    echo grant 'guest', 'RWC' | hbase shell

If you are using a cluster secured with Kerberos you will need to have used `kinit` to authenticate to the KDC.

#### HBase REST API Setup ####

#### Launch REST API ####

The command below launches the REST daemon on port 8080 (the default)

    sudo {HBASE_BIN}/hbase-daemon.sh start rest

Where `{HBASE_BIN}` is `/usr/hdp/current/hbase-master/bin/` in the case of a HDP install.

To use a different port use the `-p` option:

    sudo {HBASE_BIN/hbase-daemon.sh start rest -p 60080

#### Configure Sandbox port mapping for VirtualBox ####

1. Select the VM
2. Select menu Machine>Settings...
3. Select tab Network
4. Select Adapter 1
5. Press Port Forwarding button
6. Press Plus button to insert new rule: Name=HBASE REST, Host Port=60080, Guest Port=60080
7. Press OK to close the rule window
8. Press OK to Network window save the changes

#### HBase Restart ####

If it becomes necessary to restart HBase you can log into the hosts running HBase and use these steps.

    sudo {HBASE_BIN}/hbase-daemon.sh stop rest
    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh stop regionserver
    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh stop master
    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh stop zookeeper

    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh start regionserver
    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh start master
    sudo -u hbase {HBASE_BIN}/hbase-daemon.sh start zookeeper
    sudo {HBASE_BIN}/hbase-daemon.sh start rest -p 60080

Where `{HBASE_BIN}` is `/usr/hdp/current/hbase-master/bin/` in the case of a HDP Sandbox install.
 
#### HBase client DSL ####

For more details about client DSL usage please look at the chapter about the client DSL in this guide.

After launching the shell, execute the following command to be able to use the snippets below.
`import org.apache.knox.gateway.shell.hbase.HBase;`
 
#### systemVersion() - Query Software Version.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).systemVersion().now().string`

#### clusterVersion() - Query Storage Cluster Version.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).clusterVersion().now().string`

#### status() - Query Storage Cluster Status.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).status().now().string`

#### table().list() - Query Table List.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
  * `HBase.session(session).table().list().now().string`

#### table(String tableName).schema() - Query Table Schema.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).table().schema().now().string`

#### table(String tableName).create() - Create Table Schema.

* Request
    * attribute(String name, Object value) - the table's attribute.
    * family(String name) - starts family definition. Has sub requests:
    * attribute(String name, Object value) - the family's attribute.
    * endFamilyDef() - finishes family definition.
* Response
    * EmptyResponse
* Example


    HBase.session(session).table(tableName).create()
       .attribute("tb_attr1", "value1")
       .attribute("tb_attr2", "value2")
       .family("family1")
           .attribute("fm_attr1", "value3")
           .attribute("fm_attr2", "value4")
       .endFamilyDef()
       .family("family2")
       .family("family3")
       .endFamilyDef()
       .attribute("tb_attr3", "value5")
       .now()

#### table(String tableName).update() - Update Table Schema.

* Request
    * family(String name) - starts family definition. Has sub requests:
    * attribute(String name, Object value) - the family's attribute.
    * endFamilyDef() - finishes family definition.
* Response
    * EmptyResponse
* Example


    HBase.session(session).table(tableName).update()
         .family("family1")
             .attribute("fm_attr1", "new_value3")
         .endFamilyDef()
         .family("family4")
             .attribute("fm_attr3", "value6")
         .endFamilyDef()
         .now()```

#### table(String tableName).regions() - Query Table Metadata.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).table(tableName).regions().now().string`

#### table(String tableName).delete() - Delete Table.

* Request
    * No request parameters.
* Response
    * EmptyResponse
* Example
    * `HBase.session(session).table(tableName).delete().now()`

#### table(String tableName).row(String rowId).store() - Cell Store.

* Request
    * column(String family, String qualifier, Object value, Long time) - the data to store; "qualifier" may be "null"; "time" is optional.
* Response
    * EmptyResponse
* Example


    HBase.session(session).table(tableName).row("row_id_1").store()
         .column("family1", "col1", "col_value1")
         .column("family1", "col2", "col_value2", 1234567890l)
         .column("family2", null, "fam_value1")
         .now()


    HBase.session(session).table(tableName).row("row_id_2").store()
         .column("family1", "row2_col1", "row2_col_value1")
         .now()

#### table(String tableName).row(String rowId).query() - Cell or Row Query.

* rowId is optional. Querying with null or empty rowId will select all rows.
* Request
    * column(String family, String qualifier) - the column to select; "qualifier" is optional.
    * startTime(Long) - the lower bound for filtration by time.
    * endTime(Long) - the upper bound for filtration by time.
    * times(Long startTime, Long endTime) - the lower and upper bounds for filtration by time.
    * numVersions(Long) - the maximum number of versions to return.
* Response
    * BasicResponse
* Example


    HBase.session(session).table(tableName).row("row_id_1")
         .query()
         .now().string


    HBase.session(session).table(tableName).row().query().now().string


    HBase.session(session).table(tableName).row().query()
         .column("family1", "row2_col1")
         .column("family2")
         .times(0, Long.MAX_VALUE)
         .numVersions(1)
         .now().string

#### table(String tableName).row(String rowId).delete() - Row, Column, or Cell Delete.

* Request
    * column(String family, String qualifier) - the column to delete; "qualifier" is optional.
    * time(Long) - the upper bound for time filtration.
* Response
    * EmptyResponse
* Example


    HBase.session(session).table(tableName).row("row_id_1")
         .delete()
         .column("family1", "col1")
         .now()```


    HBase.session(session).table(tableName).row("row_id_1")
         .delete()
         .column("family2")
         .time(Long.MAX_VALUE)
         .now()```

#### table(String tableName).scanner().create() - Scanner Creation.

* Request
    * startRow(String) - the lower bound for filtration by row id.
    * endRow(String) - the upper bound for filtration by row id.
    * rows(String startRow, String endRow) - the lower and upper bounds for filtration by row id.
    * column(String family, String qualifier) - the column to select; "qualifier" is optional.
    * batch(Integer) - the batch size.
    * startTime(Long) - the lower bound for filtration by time.
    * endTime(Long) - the upper bound for filtration by time.
    * times(Long startTime, Long endTime) - the lower and upper bounds for filtration by time.
    * filter(String) - the filter XML definition.
    * maxVersions(Integer) - the maximum number of versions to return.
* Response
    * scannerId : String - the scanner ID of the created scanner. Consumes body.
* Example


    HBase.session(session).table(tableName).scanner().create()
         .column("family1", "col2")
         .column("family2")
         .startRow("row_id_1")
         .endRow("row_id_2")
         .batch(1)
         .startTime(0)
         .endTime(Long.MAX_VALUE)
         .filter("")
         .maxVersions(100)
         .now()```

#### table(String tableName).scanner(String scannerId).getNext() - Scanner Get Next.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `HBase.session(session).table(tableName).scanner(scannerId).getNext().now().string`

#### table(String tableName).scanner(String scannerId).delete() - Scanner Deletion.

* Request
    * No request parameters.
* Response
    * EmptyResponse
* Example
    * `HBase.session(session).table(tableName).scanner(scannerId).delete().now()`

### HBase via Client DSL ###

This example illustrates sequence of all basic HBase operations: 
1. get system version
2. get cluster version
3. get cluster status
4. create the table
5. get list of tables
6. get table schema
7. update table schema
8. insert single row into table
9. query row by id
10. query all rows
11. delete cell from row
12. delete entire column family from row
13. get table regions
14. create scanner
15. fetch values using scanner
16. drop scanner
17. drop the table

There are several ways to do this depending upon your preference.

You can use the Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar samples/ExampleHBase.groovy

You can manually type in the KnoxShell DSL script into the interactive Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar

Each line from the file below will need to be typed or copied into the interactive shell.

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements.  See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership.  The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License.  You may obtain a copy of the License at
     *
     *     https://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    package org.apache.knox.gateway.shell.hbase

    import org.apache.knox.gateway.shell.Hadoop

    import static java.util.concurrent.TimeUnit.SECONDS

    gateway = "https://localhost:8443/gateway/sandbox"
    username = "guest"
    password = "guest-password"
    tableName = "test_table"

    session = Hadoop.login(gateway, username, password)

    println "System version : " + HBase.session(session).systemVersion().now().string

    println "Cluster version : " + HBase.session(session).clusterVersion().now().string

    println "Status : " + HBase.session(session).status().now().string

    println "Creating table '" + tableName + "'..."

    HBase.session(session).table(tableName).create()  \
        .attribute("tb_attr1", "value1")  \
        .attribute("tb_attr2", "value2")  \
        .family("family1")  \
            .attribute("fm_attr1", "value3")  \
            .attribute("fm_attr2", "value4")  \
        .endFamilyDef()  \
        .family("family2")  \
        .family("family3")  \
        .endFamilyDef()  \
        .attribute("tb_attr3", "value5")  \
        .now()

    println "Done"

    println "Table List : " + HBase.session(session).table().list().now().string

    println "Schema for table '" + tableName + "' : " + HBase.session(session)  \
        .table(tableName)  \
        .schema()  \
        .now().string

    println "Updating schema of table '" + tableName + "'..."

    HBase.session(session).table(tableName).update()  \
        .family("family1")  \
            .attribute("fm_attr1", "new_value3")  \
        .endFamilyDef()  \
        .family("family4")  \
            .attribute("fm_attr3", "value6")  \
        .endFamilyDef()  \
        .now()

    println "Done"

    println "Schema for table '" + tableName + "' : " + HBase.session(session)  \
        .table(tableName)  \
        .schema()  \
        .now().string

    println "Inserting data into table..."

    HBase.session(session).table(tableName).row("row_id_1").store()  \
        .column("family1", "col1", "col_value1")  \
        .column("family1", "col2", "col_value2", 1234567890l)  \
        .column("family2", null, "fam_value1")  \
        .now()

    HBase.session(session).table(tableName).row("row_id_2").store()  \
        .column("family1", "row2_col1", "row2_col_value1")  \
        .now()

    println "Done"

    println "Querying row by id..."

    println HBase.session(session).table(tableName).row("row_id_1")  \
        .query()  \
        .now().string

    println "Querying all rows..."

    println HBase.session(session).table(tableName).row().query().now().string

    println "Querying row by id with extended settings..."

    println HBase.session(session).table(tableName).row().query()  \
        .column("family1", "row2_col1")  \
        .column("family2")  \
        .times(0, Long.MAX_VALUE)  \
        .numVersions(1)  \
        .now().string

    println "Deleting cell..."

    HBase.session(session).table(tableName).row("row_id_1")  \
        .delete()  \
        .column("family1", "col1")  \
        .now()

    println "Rows after delete:"

    println HBase.session(session).table(tableName).row().query().now().string

    println "Extended cell delete"

    HBase.session(session).table(tableName).row("row_id_1")  \
        .delete()  \
        .column("family2")  \
        .time(Long.MAX_VALUE)  \
        .now()

    println "Rows after delete:"

    println HBase.session(session).table(tableName).row().query().now().string

    println "Table regions : " + HBase.session(session).table(tableName)  \
        .regions()  \
        .now().string

    println "Creating scanner..."

    scannerId = HBase.session(session).table(tableName).scanner().create()  \
        .column("family1", "col2")  \
        .column("family2")  \
        .startRow("row_id_1")  \
        .endRow("row_id_2")  \
        .batch(1)  \
        .startTime(0)  \
        .endTime(Long.MAX_VALUE)  \
        .filter("")  \
        .maxVersions(100)  \
        .now().scannerId

    println "Scanner id=" + scannerId

    println "Scanner get next..."

    println HBase.session(session).table(tableName).scanner(scannerId)  \
        .getNext()  \
        .now().string

    println "Dropping scanner with id=" + scannerId

    HBase.session(session).table(tableName).scanner(scannerId).delete().now()

    println "Done"

    println "Dropping table '" + tableName + "'..."

    HBase.session(session).table(tableName).delete().now()

    println "Done"

    session.shutdown(10, SECONDS)

### HBase via cURL

#### Get software version

Set Accept Header to "text/plain", "text/xml", "application/json" or "application/x-protobuf"

    %  curl -ik -u guest:guest-password\
     -H "Accept:  application/json"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/version'

#### Get version information regarding the HBase cluster backing the REST API instance

Set Accept Header to "text/plain", "text/xml" or "application/x-protobuf"

    %  curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/version/cluster'

#### Get detailed status on the HBase cluster backing the REST API instance.

Set Accept Header to "text/plain", "text/xml", "application/json" or "application/x-protobuf"

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/status/cluster'

#### Get the list of available tables.

Set Accept Header to "text/plain", "text/xml", "application/json" or "application/x-protobuf"

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase'

#### Create table with two column families using xml input

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"   -H "Content-Type: text/xml"\
     -d '<?xml version="1.0" encoding="UTF-8"?><TableSchema name="table1"><ColumnSchema name="family1"/><ColumnSchema name="family2"/></TableSchema>'\
     -X PUT 'https://localhost:8443/gateway/sandbox/hbase/table1/schema'

#### Create table with two column families using JSON input

    curl -ik -u guest:guest-password\
     -H "Accept: application/json"  -H "Content-Type: application/json"\
     -d '{"name":"table2","ColumnSchema":[{"name":"family3"},{"name":"family4"}]}'\
     -X PUT 'https://localhost:8443/gateway/sandbox/hbase/table2/schema'

#### Get table metadata

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/table1/regions'

#### Insert single row table

    curl -ik -u guest:guest-password\
     -H "Content-Type: text/xml"\
     -H "Accept: text/xml"\
     -d '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><CellSet><Row key="cm93MQ=="><Cell column="ZmFtaWx5MTpjb2wx" >dGVzdA==</Cell></Row></CellSet>'\
     -X POST 'https://localhost:8443/gateway/sandbox/hbase/table1/row1'

#### Insert multiple rows into table

    curl -ik -u guest:guest-password\
     -H "Content-Type: text/xml"\
     -H "Accept: text/xml"\
     -d '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><CellSet><Row key="cm93MA=="><Cell column=" ZmFtaWx5Mzpjb2x1bW4x" >dGVzdA==</Cell></Row><Row key="cm93MQ=="><Cell column=" ZmFtaWx5NDpjb2x1bW4x" >dGVzdA==</Cell></Row></CellSet>'\
     -X POST 'https://localhost:8443/gateway/sandbox/hbase/table2/false-row-key'

#### Get all data from table

Set Accept Header to "text/plain", "text/xml", "application/json" or "application/x-protobuf"

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/table1/*'

#### Execute cell or row query

Set Accept Header to "text/plain", "text/xml", "application/json" or "application/x-protobuf"

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/table1/row1/family1:col1'

#### Delete entire row from table

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X DELETE 'https://localhost:8443/gateway/sandbox/hbase/table2/row0'

#### Delete column family from row

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X DELETE 'https://localhost:8443/gateway/sandbox/hbase/table2/row0/family3'

#### Delete specific column from row

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X DELETE 'https://localhost:8443/gateway/sandbox/hbase/table2/row0/family3'

#### Create scanner

Scanner URL will be in Location response header

    curl -ik -u guest:guest-password\
     -H "Content-Type: text/xml"\
     -d '<Scanner batch="1"/>'\
     -X PUT 'https://localhost:8443/gateway/sandbox/hbase/table1/scanner'

#### Get the values of the next cells found by the scanner

    curl -ik -u guest:guest-password\
     -H "Accept: application/json"\
     -X GET 'https://localhost:8443/gateway/sandbox/hbase/table1/scanner/13705290446328cff5ed'

#### Delete scanner

    curl -ik -u guest:guest-password\
     -H "Accept: text/xml"\
     -X DELETE 'https://localhost:8443/gateway/sandbox/hbase/table1/scanner/13705290446328cff5ed'

#### Delete table

    curl -ik -u guest:guest-password\
     -X DELETE 'https://localhost:8443/gateway/sandbox/hbase/table1/schema'


### HBase REST HA ###

Please look at #[Default Service HA support] if you wish to explicitly list the URLs under the service definition.

If you run the HBase REST Server from the HBase Region Server nodes, you can utilize more advanced HA support.  The HBase 
REST Server does not register itself with ZooKeeper.  So the Knox HA component looks in ZooKeeper for instances of HBase Region 
Servers and then performs a light weight ping for the presence of the REST Server on the same hosts.  The user should not supply URLs 
in the service definition.  

Note: Users of Ambari must manually startup the HBase REST Server.

To enable HA functionality for HBase in Knox the following configuration has to be added to the topology file.

    <provider>
        <role>ha</role>
        <name>HaProvider</name>
        <enabled>true</enabled>
        <param>
            <name>WEBHBASE</name>
            <value>maxFailoverAttempts=3;failoverSleep=1000;enabled=true;zookeeperEnsemble=machine1:2181,machine2:2181,machine3:2181</value>
       </param>
    </provider>

The role and name of the provider above must be as shown. The name in the 'param' section must match that of the service
role name that is being configured for HA and the value in the 'param' section is the configuration for that particular
service in HA mode. In this case the name is 'WEBHBASE'.

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
A comma separated list of host names (or IP addresses) of the ZooKeeper hosts that consist of the ensemble that the HBase 
servers register their information with. 

And for the service configuration itself the URLs need NOT be added to the list. For example:

    <service>
        <role>WEBHBASE</role>
    </service>

Please note that there is no `<url>` tag specified here as the URLs for the Kafka servers are obtained from ZooKeeper.
