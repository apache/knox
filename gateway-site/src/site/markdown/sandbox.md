<!---
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to You under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

------------------------------------------------------------------------------
Sandbox Configuration
------------------------------------------------------------------------------
This version of the Apache Knox Gateway is tested against
[Hortonworks Sandbox 1.2][sb]

In order to correct the issue with Sandbox you can use the commands below
to login to the Sandbox VM and modify the configuration.  This assumes that
the name sandbox is setup to resolve to the Sandbox VM.  It may be necessary
to use the IP address of the Sandbox VM instead.  ***This is frequently but
not always 192.168.56.101.***

    ssh root@sandbox
    cat /usr/lib/hadoop/conf/hdfs-site.xml | sed s/localhost/sandbox/ > /usr/lib/hadoop/conf/hdfs-site.xml
    shutdown -r now

In addition to make it very easy to follow along with the samples for the
gateway you can configure your local system to resolve the address of the
Sandbox by the names `vm` and `sandbox`.

On Linux or Macintosh systems add a line like this to the end of the
`/etc/hosts file on` your local machine, ***not the Sandbox VM***.
*Note: That is a _tab_ character between the 192.168.56.101 and the vm.*

    192.168.56.101	vm sandbox

On Windows systems a similar but different mechanism can be used.  On recent
versions of windows the file that should be modified is
`%systemroot%\system32\drivers\etc\hosts`

[sb]: http://hortonworks.com/products/hortonworks-sandbox/

------------------------------------------------------------------------------
Disclaimer
------------------------------------------------------------------------------
The Apache Knox Gateway is an effort undergoing incubation at the
Apache Software Foundation (ASF), sponsored by the Apache Incubator PMC.

Incubation is required of all newly accepted projects until a further review
indicates that the infrastructure, communications, and decision making process
have stabilized in a manner consistent with other successful ASF projects.

While incubation status is not necessarily a reflection of the completeness
or stability of the code, it does indicate that the project has yet to be
fully endorsed by the ASF.