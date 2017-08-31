/*
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
import org.apache.knox.gateway.shell.Hadoop
import org.apache.knox.gateway.shell.Credentials

gateway = "https://localhost:8443/gateway/sandbox"

credentials = new Credentials()
credentials.add("ClearInput", "Enter username: ", "user")
                .add("HiddenInput", "Enter pas" + "sword: ", "pass")
credentials.collect()

username = credentials.get("user").string()
pass = credentials.get("pass").string()

session = Hadoop.login( gateway, username, pass )

println "JSON=" + SampleService.simple( session ).param( "/tmp" ).now().string

println "Names=" + SampleService.complex( session ).param( "/tmp" ).now().names