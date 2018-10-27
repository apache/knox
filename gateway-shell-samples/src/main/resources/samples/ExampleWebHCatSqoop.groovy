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

// Define sqoop options value for sqoop command.
// This use publicly available Genome mysql database.
// If the database is unavailable, setup an alternate database and update the
// db information below.
db = [ driver:"com.mysql.jdbc.Driver", url:"jdbc:mysql://genome-mysql.cse.ucsc.edu/hg38", user:"genome", password:"", name:"hg38", table:"scBlastTab", split:"query" ]

targetdir = jobDir + "/" + db.table

sqoop_command = "import --driver ${db.driver} --connect ${db.url} --username ${db.user} --password ${db.password} --table ${db.table} --split-by ${db.split} --target-dir ${targetdir}"

jobId = Job.submitSqoop(session) \
            .command(sqoop_command) \
            .statusDir("${jobDir}/output") \
            .now().jobId

println "Submitted job: " + jobId

println "Polling up to 60s for job completion..."
done = false
count = 0
while( !done && count++ < 180 ) {
  sleep( 1000 )
  json = Job.queryStatus(session).jobId(jobId).now().string
  done = JsonPath.read( json, "\$.status.jobComplete" )
  print "."; System.out.flush();
}
println ""
println "Job status: " + done

text = Hdfs.ls( session ).dir( jobDir + "/output" ).now().string
json = (new JsonSlurper()).parseText( text )
println json.FileStatuses.FileStatus.pathSuffix

println "Content of stderr:"
println Hdfs.get( session ).from( jobDir + "/output/stderr" ).now().string

println "Session closed: " + session.shutdown( 10, SECONDS )
