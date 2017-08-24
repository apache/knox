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
package org.apache.knox.gateway.shell.yarn

import org.apache.knox.gateway.shell.Hadoop

gateway = "https://localhost:8443/gateway/sandbox"
username = "guest"
password = "guest-password"

hadoop = Hadoop.login( gateway, username, password )
appId = Yarn.newApp(hadoop).now().getAppId()

submitReq = """
{
    "application-id": "$appId",
    "application-name": "test",
    "am-container-spec": {
        "local-resources": {
            "entry": {
                "key": "AppMaster.jar",
                "value": {
                    "resource": "hdfs://localhost:9000/user/guest/AppMaster.jar",
                    "type": "FILE",
                    "visibility": "APPLICATION",
                    "size": "41601",
                    "timestamp": "1405544667528"
                }
            }
        },
        "commands": {
            "command": "{{JAVA_HOME}}/bin/java -Xmx10m org.apache.hadoop.yarn.applications.distributedshell.ApplicationMaster --container_memory 10 --container_vcores 1 --num_containers 1 --priority 0 1><LOG_DIR>/AppMaster.stdout 2><LOG_DIR>/AppMaster.stderr"
        },
        "environment": {
            "entry": [
                {
                    "key": "DISTRIBUTEDSHELLSCRIPTTIMESTAMP",
                    "value": "1405545208994"
                },
                {
                    "key": "CLASSPATH",
                    "value": "{{CLASSPATH}}<CPS>./*<CPS>{{HADOOP_CONF_DIR}}<CPS>{{HADOOP_COMMON_HOME}}/share/hadoop/common/*<CPS>{{HADOOP_COMMON_HOME}}/share/hadoop/common/lib/*<CPS>{{HADOOP_HDFS_HOME}}/share/hadoop/hdfs/*<CPS>{{HADOOP_HDFS_HOME}}/share/hadoop/hdfs/lib/*<CPS>{{HADOOP_YARN_HOME}}/share/hadoop/yarn/*<CPS>{{HADOOP_YARN_HOME}}/share/hadoop/yarn/lib/*<CPS>./log4j.properties"
                },
                {
                    "key": "DISTRIBUTEDSHELLSCRIPTLEN",
                    "value": "50"
                },
                {
                    "key": "DISTRIBUTEDSHELLSCRIPTLOCATION",
                    "value": "hdfs://localhost:9000/user/guest/shellCommands.sh"
                }
            ]
        }
    },
    "unmanaged-AM": "false",
    "max-app-attempts": "2",
    "resource": {
        "memory": "1024",
        "vCores": "1"
    },
    "application-type": "YARN",
    "keep-containers-across-application-attempts": "false"
}
"""

Yarn.submitApp(hadoop).text(submitReq).now()

println(Yarn.appState(hadoop).appId(appId).now().getState())

println(Yarn.killApp(hadoop).appId(appId).now().string)


