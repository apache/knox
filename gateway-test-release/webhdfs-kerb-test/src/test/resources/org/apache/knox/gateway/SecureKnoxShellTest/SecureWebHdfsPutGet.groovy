package org.apache.knox.gateway.ShellTest

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
import org.apache.knox.gateway.shell.KnoxSession
import org.apache.knox.gateway.shell.hdfs.Hdfs

dataFile = "README"
username = "guest"
dataDir = "/user/" + username + "/example"

/* Unit test will add 'gateway' and 'jaasConf' binding programmatically */
session = KnoxSession.kerberosLogin(gateway, jaasConf, krb5conf, true)

status = Hdfs.status(session).file( "/" ).now().string

Hdfs.put( session ).file( file ).to( dataDir + "/" + dataFile ).now()
Hdfs.put( session ).file( file ).to( dataDir + "/" + dataFile ).overwrite(true).permission(777).now()

fetchedFile = Hdfs.get( session ).from( dataDir + "/" + dataFile).now().string

session.shutdown()
