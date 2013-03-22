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
import org.apache.hadoop.gateway.shell.job.Job

import static java.util.concurrent.TimeUnit.SECONDS

gateway = "https://localhost:8443/gateway/sample"
username = "mapred"
password = "mapred-password"
dataFile = "LICENSE"
jarFile = "samples/hadoop-examples.jar"

hadoop = Hadoop.login( gateway, username, password )

println "Delete /tmp/test " + Hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now().statusCode
println "Create /tmp/test " + Hdfs.mkdir(hadoop).dir( "/tmp/test").now().statusCode

putData = Hdfs.put(hadoop).file( dataFile ).to( "/tmp/test/input/FILE" ).later() {
  println "Put /tmp/test/input/FILE " + it.statusCode }
putJar = Hdfs.put(hadoop).file( jarFile ).to( "/tmp/test/hadoop-examples.jar" ).later() {
  println "Put /tmp/test/hadoop-examples.jar " + it.statusCode }
hadoop.waitFor( putData, putJar )

jobId = Job.submitJava(hadoop) \
  .jar( "/tmp/test/hadoop-examples.jar" ) \
  .app( "wordcount" ) \
  .input( "/tmp/test/input" ) \
  .output( "/tmp/test/output" ) \
  .now().jobId
println "Submitted job " + jobId

println "Polling for completion..."
done = false
count = 0
while( !done && count++ < 60 ) {
  sleep( 1000 )
  json = Job.queryStatus(hadoop).jobId(jobId).now().string
  done = JsonPath.read( json, "\$.status.jobComplete" )
}
println "Done " + done

println "Shutdown " + hadoop.shutdown( 10, SECONDS )

