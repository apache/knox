package org.apache.hadoop.gateway.shell

import org.apache.hadoop.gateway.shell.hdfs.Hdfs
import org.apache.hadoop.gateway.shell.job.Job
import org.apache.hadoop.gateway.shell.hadoop.Hadoop

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
hadoop = Hadoop.login( "http://localhost:8443/gateway/sample", "mapred", "mapred-password" )
println Hdfs.ls(hadoop).dir( "/" ).go().json
Hdfs.rm(hadoop).file( "/tmp/test" ).recursive().go()
Hdfs.mkdir(hadoop).dir( "/tmp/test").go()
Hdfs.put(hadoop).from("LICENSE").to("/tmp/test/input/LICENSE").go()
Hdfs.put(hadoop).from("hadoop-examples.jar").to("/tmp/test/hadoop-examples.jar").go()
println Job.java(hadoop) \
    .jar("/tmp/test/hadoop-examples.jar") \
    .app("wordcount") \
    .input("/temp/test/input") \
    .output("/temp/test/output") \
    .go().jobId


