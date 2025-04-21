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

### WebHCat ###

WebHCat (also called _Templeton_) is a related but separate service from HiveServer2.
As such it is installed and configured independently.
The [WebHCat wiki pages](https://cwiki.apache.org/confluence/display/Hive/WebHCat) describe this processes.
In sandbox this configuration file for WebHCat is located at `/etc/hadoop/hcatalog/webhcat-site.xml`.
Note the properties shown below as they are related to configuration required by the gateway.

    <property>
        <name>templeton.port</name>
        <value>50111</value>
    </property>

Also important is the configuration of the JOBTRACKER RPC endpoint.
For Hadoop 2 this can be found in the `yarn-site.xml` file.
In Sandbox this file can be found at `/etc/hadoop/conf/yarn-site.xml`.
The property `yarn.resourcemanager.address` within that file is relevant for the gateway's configuration.

    <property>
        <name>yarn.resourcemanager.address</name>
        <value>sandbox.hortonworks.com:8050</value>
    </property>

See #[WebHDFS] for details about locating the Hadoop configuration for the NAMENODE endpoint.

The gateway by default includes a sample topology descriptor file `{GATEWAY_HOME}/deployments/sandbox.xml`.
The values in this sample are configured to work with an installed Sandbox VM.

    <service>
        <role>NAMENODE</role>
        <url>hdfs://localhost:8020</url>
    </service>
    <service>
        <role>JOBTRACKER</role>
        <url>rpc://localhost:8050</url>
    </service>
    <service>
        <role>WEBHCAT</role>
        <url>http://localhost:50111/templeton</url>
    </service>

The URLs provided for the role NAMENODE and JOBTRACKER do not result in an endpoint being exposed by the gateway.
This information is only required so that other URLs can be rewritten that reference the appropriate RPC address for Hadoop services.
This prevents clients from needing to be aware of the internal cluster details.
Note that for Hadoop 2 the JOBTRACKER RPC endpoint is provided by the Resource Manager component.

By default the gateway is configured to use the HTTP endpoint for WebHCat in the Sandbox.
This could alternatively be configured to use the HTTPS endpoint by providing the correct address.

#### WebHCat URL Mapping ####

For WebHCat URLs, the mapping of Knox Gateway accessible URLs to direct WebHCat URLs is simple.

| ------- | ------------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/templeton` |
| Cluster | `http://{webhcat-host}:{webhcat-port}/templeton}`                               |


#### WebHCat via cURL

Users can use cURL to directly invoke the REST APIs via the gateway. For the full list of available REST calls look at the WebHCat documentation. This is a simple curl command to test the connection:

    curl -i -k -u guest:guest-password 'https://localhost:8443/gateway/sandbox/templeton/v1/status'


#### WebHCat Example ####

This example will submit the familiar WordCount Java MapReduce job to the Hadoop cluster via the gateway using the KnoxShell DSL.
There are several ways to do this depending upon your preference.

You can use the "embedded" Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar samples/ExampleWebHCatJob.groovy

You can manually type in the KnoxShell DSL script into the "embedded" Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar

Each line from the file `samples/ExampleWebHCatJob.groovy` would then need to be typed or copied into the interactive shell.

#### WebHCat Client DSL ####

##### submitJava() - Submit a Java MapReduce job.

* Request
    * jar (String) - The remote file name of the JAR containing the app to execute.
    * app (String) - The app name to execute. This is _wordcount_ for example not the class name.
    * input (String) - The remote directory name to use as input for the job.
    * output (String) - The remote directory name to store output from the job.
* Response
    * jobId : String - The job ID of the submitted job.  Consumes body.
* Example


    Job.submitJava(session)
        .jar(remoteJarName)
        .app(appName)
        .input(remoteInputDir)
        .output(remoteOutputDir)
        .now()
        .jobId

##### submitPig() - Submit a Pig job.

* Request
    * file (String) - The remote file name of the pig script.
    * arg (String) - An argument to pass to the script.
    * statusDir (String) - The remote directory to store status output.
* Response
    * jobId : String - The job ID of the submitted job.  Consumes body.
* Example
    * `Job.submitPig(session).file(remotePigFileName).arg("-v").statusDir(remoteStatusDir).now()`

##### submitHive() - Submit a Hive job.

* Request
    * file (String) - The remote file name of the hive script.
    * arg (String) - An argument to pass to the script.
    * statusDir (String) - The remote directory to store status output.
* Response
    * jobId : String - The job ID of the submitted job.  Consumes body.
* Example
    * `Job.submitHive(session).file(remoteHiveFileName).arg("-v").statusDir(remoteStatusDir).now()`

#### submitSqoop Job API ####
Using the Knox DSL, you can now easily submit and monitor [Apache Sqoop](https://sqoop.apache.org) jobs. The WebHCat Job class now supports the `submitSqoop` command.

    Job.submitSqoop(session)
        .command("import --connect jdbc:mysql://hostname:3306/dbname ... ")
        .statusDir(remoteStatusDir)
        .now().jobId

The `submitSqoop` command supports the following arguments:

* command (String) - The sqoop command string to execute.
* files (String) - Comma separated files to be copied to the templeton controller job.
* optionsfile (String) - The remote file which contain Sqoop command need to run.
* libdir (String) - The remote directory containing jdbc jar to include with sqoop lib
* statusDir (String) - The remote directory to store status output.

A complete example is available here: https://cwiki.apache.org/confluence/display/KNOX/2016/11/08/Running+SQOOP+job+via+KNOX+Shell+DSL


##### queryQueue() - Return a list of all job IDs registered to the user.

* Request
    * No request parameters.
* Response
    * BasicResponse
* Example
    * `Job.queryQueue(session).now().string`

##### queryStatus() - Check the status of a job and get related job information given its job ID.

* Request
    * jobId (String) - The job ID to check. This is the ID received when the job was created.
* Response
    * BasicResponse
* Example
    * `Job.queryStatus(session).jobId(jobId).now().string`

### WebHCat HA ###

Please look at #[Default Service HA support]
