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

import com.jayway.jsonpath.JsonPath
import org.apache.hadoop.gateway.shell.Hadoop
import org.apache.hadoop.gateway.shell.hdfs.Hdfs
import org.apache.hadoop.gateway.shell.workflow.Workflow

import static java.util.concurrent.TimeUnit.SECONDS

gateway = "https://localhost:8443/gateway/sample"
jobTracker = "sandbox:50300";
nameNode = "sandbox:8020";
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

println "Delete /tmp/test " + Hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now().statusCode
println "Mkdir /tmp/test " + Hdfs.mkdir(hadoop).dir( "/tmp/test").now().statusCode
putWorkflow = Hdfs.put(hadoop).text( definition ).to( "/tmp/test/workflow.xml" ).later() {
  println "Put /tmp/test/workflow.xml " + it.statusCode }
putData = Hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/FILE" ).later() {
  println "Put /tmp/test/input/FILE " + it.statusCode }
putJar = Hdfs.put(hadoop).file( jarFile ).to( "/tmp/test/lib/hadoop-examples.jar" ).later() {
  println "Put /tmp/test/lib/hadoop-examples.jar " + it.statusCode }
hadoop.waitFor( putWorkflow, putData, putJar )

jobId = Workflow.submit(hadoop).text( configuration ).now().jobId
println "Submit job " + jobId

status = "UNKNOWN";
count = 0;
while( status != "SUCCEEDED" && count++ < 60 ) {
  sleep( 1000 )
  json = Workflow.status(hadoop).jobId( jobId ).now().string
  status = JsonPath.read( json, "\$.status" )
}
println "Job status " + status;

println "Shutdown " + hadoop.shutdown( 10, SECONDS )
