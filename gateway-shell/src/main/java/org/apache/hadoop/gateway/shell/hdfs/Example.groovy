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
package org.apache.hadoop.gateway.shell.hdfs

import org.apache.hadoop.gateway.shell.hdfs.Hdfs as hdfs

import org.apache.hadoop.gateway.shell.Hadoop

gateway = "https://localhost:8443/gateway/sample"
username = "mapred"
password = "mapred-password"
inputFile = "/Users/kevin.minder/Projects/gateway-0.2.0-SNAPSHOT/LICENSE"
jarFile = "/Users/kevin.minder/Projects/gateway-0.2.0-SNAPSHOT/hadoop-examples.jar"

hadoop = Hadoop.login( gateway, username, password )

println Hdfs.ls(hadoop).dir( "/" ).now().string

hdfs.rm(hadoop).file( "/tmp/test" ).recursive().now()

hdfs.mkdir(hadoop).dir( "/tmp/test").now()

hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/LICENSE" ).now()

hdfs.get(hadoop).file( "/Users/kevin.minder/Projects/gateway-0.2.0-SNAPSHOT/OUTPUT" ).from( "/tmp/test/input/LICENSE" ).now()

future = hdfs.put(hadoop).file( inputFile ).to( "/tmp/test/input/LICENSE2" ).later()
println future.get().statusCode
