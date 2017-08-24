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
package org.apache.knox.gateway.shell.hdfs

import org.apache.knox.gateway.shell.Hadoop

import static java.util.concurrent.TimeUnit.SECONDS

gateway = "https://localhost:8443/gateway/sandbox"
username = "mapred"
password = "mapred-password"
jarFile = "samples/hadoop-examples.jar"
inputFile = "LICENSE"
outputFile = "OUTPUT"

hadoop = Hadoop.login( gateway, username, password )

println Hdfs.ls(hadoop).dir( "/" ).now().string

println Hdfs.status(hadoop).file( "/" ).now().string

Hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now()

Hdfs.mkdir(hadoop).dir( "/tmp/test").now()

Hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/LICENSE" ).now()

future = Hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/LICENSE2" ).later()
println "Done=" + future.isDone()
hadoop.waitFor( future )
println "Status=" + future.get().statusCode

future = Hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/LICENSE3" ).later() { println "Status=" + it.statusCode }
hadoop.waitFor( future )
println "Status=" + future.get().statusCode

Hdfs.get(hadoop).file( outputFile ).from( "/tmp/test/input/LICENSE" ).now()

hadoop.shutdown( 10, SECONDS );



