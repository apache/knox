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
package org.apache.knox.gateway.shell.hbase

import org.apache.knox.gateway.shell.KnoxSession

import static java.util.concurrent.TimeUnit.SECONDS

import org.apache.knox.gateway.shell.Credentials

gateway = "https://localhost:8443/gateway/sandbox"
tableName = "test_table"

credentials = new Credentials()
credentials.add("ClearInput", "Enter username: ", "user")
                .add("HiddenInput", "Enter pas" + "sword: ", "pass")
credentials.collect()

username = credentials.get("user").string()
pass = credentials.get("pass").string()

session = KnoxSession.login(gateway, username, pass)

println "System version : " + HBase.session(session).systemVersion().now().string

println "Cluster version : " + HBase.session(session).clusterVersion().now().string

println "Status : " + HBase.session(session).status().now().string

println "Creating table '" + tableName + "'..."

HBase.session(session).table(tableName).create()  \
    .attribute("tb_attr1", "value1")  \
    .attribute("tb_attr2", "value2")  \
    .family("family1")  \
        .attribute("fm_attr1", "value3")  \
        .attribute("fm_attr2", "value4")  \
    .endFamilyDef()  \
    .family("family2")  \
    .family("family3")  \
    .endFamilyDef()  \
    .attribute("tb_attr3", "value5")  \
    .now()

println "Done"

println "Table List : " + HBase.session(session).table().list().now().string

println "Schema for table '" + tableName + "' : " + HBase.session(session)  \
    .table(tableName)  \
    .schema()  \
    .now().string

println "Updating schema of table '" + tableName + "'..."

HBase.session(session).table(tableName).update()  \
    .family("family1")  \
        .attribute("fm_attr1", "new_value3")  \
    .endFamilyDef()  \
    .family("family4")  \
        .attribute("fm_attr3", "value6")  \
    .endFamilyDef()  \
    .now()

println "Done"

println "Schema for table '" + tableName + "' : " + HBase.session(session)  \
    .table(tableName)  \
    .schema()  \
    .now().string

println "Inserting data into table..."

HBase.session(session).table(tableName).row("row_id_1").store()  \
    .column("family1", "col1", "col_value1")  \
    .column("family1", "col2", "col_value2", 1234567890l)  \
    .column("family2", null, "fam_value1")  \
    .now()

HBase.session(session).table(tableName).row("row_id_2").store()  \
    .column("family1", "row2_col1", "row2_col_value1")  \
    .now()

println "Done"

println "Querying row by id..."

println HBase.session(session).table(tableName).row("row_id_1")  \
    .query()  \
    .now().string

println "Querying all rows..."

println HBase.session(session).table(tableName).row().query().now().string

println "Querying row by id with extended settings..."

println HBase.session(session).table(tableName).row().query()  \
    .column("family1", "row2_col1")  \
    .column("family2")  \
    .times(0, Long.MAX_VALUE)  \
    .numVersions(1)  \
    .now().string

println "Deleting cell..."

HBase.session(session).table(tableName).row("row_id_1")  \
    .delete()  \
    .column("family1", "col1")  \
    .now()

println "Rows after delete:"

println HBase.session(session).table(tableName).row().query().now().string

println "Extended cell delete"

HBase.session(session).table(tableName).row("row_id_1")  \
    .delete()  \
    .column("family2")  \
    .time(Long.MAX_VALUE)  \
    .now()

println "Rows after delete:"

println HBase.session(session).table(tableName).row().query().now().string

println "Table regions : " + HBase.session(session).table(tableName)  \
    .regions()  \
    .now().string

println "Creating scanner..."

scannerId = HBase.session(session).table(tableName).scanner().create()  \
    .column("family1", "col2")  \
    .column("family2")  \
    .startRow("row_id_1")  \
    .endRow("row_id_2")  \
    .batch(1)  \
    .startTime(0)  \
    .endTime(Long.MAX_VALUE)  \
    .filter("")  \
    .maxVersions(100)  \
    .now().scannerId

println "Scanner id=" + scannerId

println "Scanner get next..."

println HBase.session(session).table(tableName).scanner(scannerId)  \
    .getNext()  \
    .now().string

println "Dropping scanner with id=" + scannerId

HBase.session(session).table(tableName).scanner(scannerId).delete().now()

println "Done"

println "Dropping table '" + tableName + "'..."

HBase.session(session).table(tableName).delete().now()

println "Done"

session.shutdown(10, SECONDS)
