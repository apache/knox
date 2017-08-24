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
package org.apache.knox.gateway.shell.job

import com.jayway.jsonpath.JsonPath
import org.apache.knox.gateway.shell.Hadoop
import org.apache.knox.gateway.shell.hdfs.Hdfs as hdfs
import org.apache.knox.gateway.shell.job.Job as job

gateway = "https://localhost:8443/gateway/sandbox"
username = "mapred"
password = "mapred-password"
inputFile = "LICENSE"

script = "select+*+from+pokes;"

hadoop = Hadoop.login( gateway, username, password )
hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now()
hdfs.put(hadoop).text( script ).to( "/tmp/test/script.hive" ).now()
hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/FILE" ).now()

jobId = job.submitHive(hadoop)
  .file( "/tmp/test/script.hive" ) \
  .arg( "-v" ) \
  .statusDir( "/tmp/test/output" ) \
  .now().jobId
println "Job=" + jobId

println job.queryQueue(hadoop).now().string

done = false;
while( !done ) {
  json = job.queryStatus(hadoop).jobId(jobId).now().string
  done = JsonPath.read( json, "\$.status.jobComplete" )
  sleep( 1000 )
  print "."
}
println "done"
