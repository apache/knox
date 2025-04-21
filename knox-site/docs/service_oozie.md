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

### Oozie ###


Oozie is a Hadoop component that provides complex job workflows to be submitted and managed.
Please refer to the latest [Oozie documentation](http://oozie.apache.org/docs/4.2.0/) for details.

In order to make Oozie accessible via the gateway there are several important Hadoop configuration settings.
These all relate to the network endpoint exposed by various Hadoop services.

The HTTP endpoint at which Oozie is running can be found via the `oozie.base.url property` in the `oozie-site.xml` file.
In a Sandbox installation this can typically be found in `/etc/oozie/conf/oozie-site.xml`.

    <property>
        <name>oozie.base.url</name>
        <value>http://sandbox.hortonworks.com:11000/oozie</value>
    </property>

The RPC address at which the Resource Manager exposes the JOBTRACKER endpoint can be found via the `yarn.resourcemanager.address` in the `yarn-site.xml` file.
In a Sandbox installation this can typically be found in `/etc/hadoop/conf/yarn-site.xml`.

    <property>
        <name>yarn.resourcemanager.address</name>
        <value>sandbox.hortonworks.com:8050</value>
    </property>

The RPC address at which the Name Node exposes its RPC endpoint can be found via the `dfs.namenode.rpc-address` in the `hdfs-site.xml` file.
In a Sandbox installation this can typically be found in `/etc/hadoop/conf/hdfs-site.xml`.

    <property>
        <name>dfs.namenode.rpc-address</name>
        <value>sandbox.hortonworks.com:8020</value>
    </property>

If HDFS has been configured to be in High Availability mode (HA), then instead of the RPC address mentioned above for the Name Node, look up and use the logical name of the service found via `dfs.nameservices` in `hdfs-site.xml`. For example,

    <property>
        <name>dfs.nameservices</name>
        <value>ha-service</value>
    </property>

Please note, only one of the URLs, either the RPC endpoint or the HA service name should be used as the NAMENODE HDFS URL in the gateway topology file.

The information above must be provided to the gateway via a topology descriptor file.
These topology descriptor files are placed in `{GATEWAY_HOME}/deployments`.
An example that is setup for the default configuration of the Sandbox is `{GATEWAY_HOME}/deployments/sandbox.xml`.
These values will need to be changed for non-default Sandbox or other Hadoop cluster configuration.

    <service>
        <role>NAMENODE</role>
        <url>hdfs://localhost:8020</url>
    </service>
    <service>
        <role>JOBTRACKER</role>
        <url>rpc://localhost:8050</url>
    </service>
    <service>
        <role>OOZIE</role>
        <url>http://localhost:11000/oozie</url>
        <param>
            <name>replayBufferSize</name>
            <value>8</value>
        </param>
    </service>

A default replayBufferSize of 8KB is shown in the sample topology file above.  This may need to be increased if your request size is larger.

#### Oozie URL Mapping ####

For Oozie URLs, the mapping of Knox Gateway accessible URLs to direct Oozie URLs is simple.

| ------- | --------------------------------------------------------------------------- |
| Gateway | `https://{gateway-host}:{gateway-port}/{gateway-path}/{cluster-name}/oozie` |
| Cluster | `http://{oozie-host}:{oozie-port}/oozie}`                                   |


#### Oozie Request Changes ####

TODO - In some cases the Oozie requests needs to be slightly different when made through the gateway.
These changes are required in order to protect the client from knowing the internal structure of the Hadoop cluster.


#### Oozie Example via Client DSL ####

This example will also submit the familiar WordCount Java MapReduce job to the Hadoop cluster via the gateway using the KnoxShell DSL.
However in this case the job will be submitted via a Oozie workflow.
There are several ways to do this depending upon your preference.

You can use the "embedded" Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar samples/ExampleOozieWorkflow.groovy

You can manually type in the KnoxShell DSL script into the "embedded" Groovy interpreter provided with the distribution.

    java -jar bin/shell.jar

Each line from the file `samples/ExampleOozieWorkflow.groovy` will need to be typed or copied into the interactive shell.

#### Oozie Example via cURL

The example below illustrates the sequence of curl commands that could be used to run a "word count" map reduce job via an Oozie workflow.

It utilizes the hadoop-examples.jar from a Hadoop install for running a simple word count job.
A copy of that jar has been included in the samples directory for convenience.

In addition a workflow definition and configuration file is required.
These have not been included but are available for download.
Download [workflow-definition.xml](workflow-definition.xml) and [workflow-configuration.xml](workflow-configuration.xml) and store them in the `{GATEWAY_HOME}` directory.
Review the contents of workflow-configuration.xml to ensure that it matches your environment.

Take care to follow the instructions below where replacement values are required.
These replacement values are identified with `{ }` markup.

    # 0. Optionally cleanup the test directory in case a previous example was run without cleaning up.
    curl -i -k -u guest:guest-password -X DELETE \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example?op=DELETE&recursive=true'

    # 1. Create the inode for workflow definition file in /user/guest/example
    curl -i -k -u guest:guest-password -X PUT \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example/workflow.xml?op=CREATE'

    # 2. Upload the workflow definition file.  This file can be found in {GATEWAY_HOME}/templates
    curl -i -k -u guest:guest-password -T workflow-definition.xml -X PUT \
        '{Value Location header from command above}'

    # 3. Create the inode for hadoop-examples.jar in /user/guest/example/lib
    curl -i -k -u guest:guest-password -X PUT \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example/lib/hadoop-examples.jar?op=CREATE'

    # 4. Upload hadoop-examples.jar to /user/guest/example/lib.  Use a hadoop-examples.jar from a Hadoop install.
    curl -i -k -u guest:guest-password -T samples/hadoop-examples.jar -X PUT \
        '{Value Location header from command above}'

    # 5. Create the inode for a sample input file readme.txt in /user/guest/example/input.
    curl -i -k -u guest:guest-password -X PUT \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example/input/README?op=CREATE'

    # 6. Upload readme.txt to /user/guest/example/input.  Use the readme.txt in {GATEWAY_HOME}.
    # The sample below uses this README file found in {GATEWAY_HOME}.
    curl -i -k -u guest:guest-password -T README -X PUT \
        '{Value of Location header from command above}'

    # 7. Submit the job via Oozie
    # Take note of the Job ID in the JSON response as this will be used in the next step.
    curl -i -k -u guest:guest-password -H Content-Type:application/xml -T workflow-configuration.xml \
        -X POST 'https://localhost:8443/gateway/sandbox/oozie/v1/jobs?action=start'

    # 8. Query the job status via Oozie.
    curl -i -k -u guest:guest-password -X GET \
        'https://localhost:8443/gateway/sandbox/oozie/v1/job/{Job ID from JSON body}'

    # 9. List the contents of the output directory /user/guest/example/output
    curl -i -k -u guest:guest-password -X GET \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example/output?op=LISTSTATUS'

    # 10. Optionally cleanup the test directory
    curl -i -k -u guest:guest-password -X DELETE \
        'https://localhost:8443/gateway/sandbox/webhdfs/v1/user/guest/example?op=DELETE&recursive=true'

### Oozie Client DSL ###

#### submit() - Submit a workflow job.

* Request
    * text (String) - XML formatted workflow configuration string.
    * file (String) - A filename containing XML formatted workflow configuration.
    * action (String) - The initial action to take on the job.  Optional: Default is "start".
* Response
    * BasicResponse
* Example
    * `Workflow.submit(session).file(localFile).action("start").now()`

#### status() - Query the status of a workflow job.

* Request
    * jobId (String) - The job ID to check. This is the ID received when the job was created.
* Response
    * BasicResponse
* Example
    * `Workflow.status(session).jobId(jobId).now().string`

### Oozie HA ###

Please look at #[Default Service HA support]
