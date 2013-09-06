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
jobTracker = "sandbox.hortonworks.com:8050"
nameNode = "sandbox.hortonworks.com:8020"
username = "hue"
password = "hue-password"
inputFile = "LICENSE"
jarFile = "samples/hadoop-examples.jar"

definition = """\
<workflow-app xmlns="uri:oozie:workflow:0.2" name="wordcount-workflow">
    <start to="root-node"/>
    <action name="root-node">
        <java>
            <job-tracker>set-via-configuration-property</job-tracker>
            <name-node>set-via-configuration-property</name-node>
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
        <name>nameNode</name>
        <value>hdfs://$nameNode</value>
    </property>
    <property>
        <name>jobTracker</name>
        <value>$jobTracker</value>
    </property>
    <property>
        <name>oozie.wf.application.path</name>
        <value>hdfs://$nameNode/tmp/test</value>
    </property>
</configuration>
"""

session = Hadoop.login( gateway, username, password )

println "Delete /tmp/test " + Hdfs.rm( session ).file( "/tmp/test" ).recursive().now().statusCode
println "Mkdir /tmp/test " + Hdfs.mkdir( session ).dir( "/tmp/test" ).now().statusCode

putWorkflow = Hdfs.put(session).text( definition ).to( "/tmp/test/workflow.xml" ).later() {
  println "Put /tmp/test/workflow.xml " + it.statusCode }

putData = Hdfs.put(session).file( inputFile ).to( "/tmp/test/input/FILE" ).later() {
  println "Put /tmp/test/input/FILE " + it.statusCode }

putJar = Hdfs.put(session).file( jarFile ).to( "/tmp/test/lib/hadoop-examples.jar" ).later() {
  println "Put /tmp/test/lib/hadoop-examples.jar " + it.statusCode }

session.waitFor( putWorkflow, putData, putJar )

jobId = Workflow.submit(session).text( configuration ).now().jobId
println "Submitted job " + jobId

println "Polling for completion..."
status = "UNKNOWN";
count = 0;
while( status != "SUCCEEDED" && count++ < 60 ) {
  sleep( 1000 )
  json = Workflow.status(session).jobId( jobId ).now().string
  status = JsonPath.read( json, "\$.status" )
}
println "Job status " + status;

println "Shutdown " + session.shutdown( 10, SECONDS )