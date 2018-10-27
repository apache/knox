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
import groovy.json.JsonSlurper
import org.apache.knox.gateway.shell.KnoxSession
import org.apache.knox.gateway.shell.hdfs.Hdfs
import org.apache.knox.gateway.shell.job.Job

import static java.util.concurrent.TimeUnit.SECONDS
import org.apache.knox.gateway.shell.Credentials

gateway = "https://localhost:8443/gateway/sandbox"

// You will need to copy hadoop-mapreduce-samples.jar from your cluster
// and place it under samples/ directory.
// For example you might find the jar under: /usr/iop/current/hadoop-mapreduce-client
jarFile = "samples/hadoop-mapreduce-examples.jar"

credentials = new Credentials()
credentials.add("ClearInput", "Enter username: ", "user")
                .add("HiddenInput", "Enter pas" + "sword: ", "pass")
credentials.collect()

username = credentials.get("user").string()
pass = credentials.get("pass").string()

jobDir = "/user/" + username + "/test"

session = KnoxSession.login( gateway, username, pass )

println "Delete " + jobDir + ": " + Hdfs.rm( session ).file( jobDir ).recursive().now().statusCode
println "Create " + jobDir + ": " + Hdfs.mkdir( session ).dir( jobDir ).now().statusCode

putJar = Hdfs.put( session ).file( jarFile ).to( jobDir + "/lib/hadoop-mapreduce-examples.jar" ).later() {
  println "Put " + jobDir + "/lib/hadoop-mapreduce-examples.jar: " + it.statusCode }

session.waitFor( putJar )

// Run teragen with 5 mappers. It will generate 500 records of 100 bytes each.
jobId = Job.submitJava(session) \
  .jar( jobDir + "/lib/hadoop-mapreduce-examples.jar" ) \
  .app( "teragen" ) \
  .arg( "-D").arg("mapred.map.tasks=5") \
  .arg( "500" ) \
  .input( jobDir + "/input_terasort" ) \
  .now().jobId
println "Submitted job: " + jobId

println "Polling up to 60s for job completion..."
done = false
count = 0
while( !done && count++ < 90 ) {
  sleep( 1000 )
  json = Job.queryStatus(session).jobId(jobId).now().string
  done = JsonPath.read( json, "\$.status.jobComplete" )
  print "."; System.out.flush();
}
println ""
println "Job status: " + done

text = Hdfs.ls( session ).dir( jobDir + "/input_terasort" ).now().string
json = (new JsonSlurper()).parseText( text )
println json.FileStatuses.FileStatus.pathSuffix

println "Session closed: " + session.shutdown( 10, SECONDS )
