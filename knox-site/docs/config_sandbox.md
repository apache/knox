<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
<!---
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
--->

## Sandbox Configuration ##

### Sandbox 2.x Configuration ###

TODO

### Sandbox 1.x Configuration ###

TODO - Update this section to use hostmap if that simplifies things.

This version of the Apache Knox Gateway is tested against [Hortonworks Sandbox 1.x][sandbox]

Currently there is an issue with Sandbox that prevents it from being easily used with the gateway.
In order to correct the issue, you can use the commands below to login to the Sandbox VM and modify the configuration.
This assumes that the name sandbox is setup to resolve to the Sandbox VM.
It may be necessary to use the IP address of the Sandbox VM instead.
*This is frequently but not always `192.168.56.101`.*

    ssh root@sandbox
    cp /usr/lib/hadoop/conf/hdfs-site.xml /usr/lib/hadoop/conf/hdfs-site.xml.orig
    sed -e s/localhost/sandbox/ /usr/lib/hadoop/conf/hdfs-site.xml.orig > /usr/lib/hadoop/conf/hdfs-site.xml
    shutdown -r now

In addition to make it very easy to follow along with the samples for the gateway you can configure your local system to resolve the address of the Sandbox by the names `vm` and `sandbox`.
The IP address that is shown below should be that of the Sandbox VM as it is known on your system.
*This will likely, but not always, be `192.168.56.101`.*

On Linux or Macintosh systems add a line like this to the end of the file `/etc/hosts` on your local machine, *not the Sandbox VM*.
_Note: The character between the 192.168.56.101 and vm below is a *tab* character._

    192.168.56.101	vm sandbox

On Windows systems a similar but different mechanism can be used.  On recent
versions of windows the file that should be modified is `%systemroot%\system32\drivers\etc\hosts`
