/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.shell.workflow

import com.jayway.jsonpath.JsonPath
import org.apache.knox.gateway.shell.Hadoop
import org.apache.knox.gateway.shell.hdfs.Hdfs as hdfs
import org.apache.knox.gateway.shell.workflow.Workflow as workflow

gateway = "https://localhost:8443/gateway/sandbox"
jobTracker = "localhost:50300";
nameNode = "localhost:8020";
username = "mapred"
password = "mapred-password"
inputFile = "LICENSE"
jarFile = "samples/hadoop-examples.jar"

definition = """\
<workflow-app xmlns="uri:oozie:workflow:0.2" name="wordcount-workflow">
    <start to="root-node"/>
    <action name="root-node">
        <java>
            <job-tracker>$jobTracker</job-tracker>
            <name-node>hdfs://$nameNode</name-node>
            <main-class>org.apache.hadoop.examples.WordCount</main-class>
            <arg>/tmp/test/input</arg>
            <arg>/tmp/test/output</arg>
        </java>
        <ok to="end"/>
        <error to="fail"/>
    </action>
    <kill name="fail">
        <message>Java failed, error message[\${wf:errorMessage(wf:lastErrorNode())}]</message>
    </kill>
    <end name="end"/>
</workflow-app>
"""

configuration = """\
<configuration>
    <property>
        <name>user.name</name>
        <value>$username</value>
    </property>
    <property>
        <name>oozie.wf.application.path</name>
        <value>hdfs://$nameNode/tmp/test</value>
    </property>
</configuration>
"""

hadoop = Hadoop.login( gateway, username, password )

hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now()
hdfs.mkdir(hadoop).dir( "/tmp/test").now()
hdfs.put(hadoop).text( definition ).to( "/tmp/test/workflow.xml" ).now()
hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/FILE" ).now()
hdfs.put(hadoop).file( jarFile ).to( "/tmp/test/lib/hadoop-examples.jar" ).now()

jobId = workflow.submit(hadoop).text( configuration ).now().jobId
println "Job=" + jobId

status = "UNKNOWN";
while( status != "SUCCEEDED" ) {
  json = workflow.status(hadoop).jobId(jobId).now().string
  status = JsonPath.read( json, "\$.status" )
  sleep( 1000 )
  print "."
}
println "done"
