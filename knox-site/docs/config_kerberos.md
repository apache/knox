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

### Secure Clusters ###

See the Hadoop documentation for setting up a secure Hadoop cluster
http://hadoop.apache.org/docs/current/hadoop-project-dist/hadoop-common/SecureMode.html

Once you have a Hadoop cluster that is using Kerberos for authentication, you have to do the following to configure Knox to work with that cluster.

#### Create Unix account for Knox on Hadoop master nodes ####

    useradd -g hadoop knox

#### Create Kerberos principal, keytab for Knox ####

One way of doing this, assuming your KDC realm is EXAMPLE.COM, is to ssh into your host running KDC and execute `kadmin.local`
That will result in an interactive session in which you can execute commands.

ssh into your host running KDC

    kadmin.local
    add_principal -randkey knox/knox@EXAMPLE.COM
    ktadd -k knox.service.keytab -norandkey knox/knox@EXAMPLE.COM
    exit


#### Copy knox keytab to Knox host ####

Add unix account for the knox user on Knox host

    useradd -g hadoop knox

Copy knox.service.keytab created on KDC host on to your Knox host `{GATEWAY_HOME}/conf/knox.service.keytab`

    chown knox knox.service.keytab
    chmod 400 knox.service.keytab

#### Update `krb5.conf` at `{GATEWAY_HOME}/conf/krb5.conf` on Knox host ####

You could copy the `{GATEWAY_HOME}/templates/krb5.conf` file provided in the Knox binary download and customize it to suit your cluster.

#### Update `krb5JAASLogin.conf` at `/etc/knox/conf/krb5JAASLogin.conf` on Knox host ####

You could copy the `{GATEWAY_HOME}/templates/krb5JAASLogin.conf` file provided in the Knox binary download and customize it to suit your cluster.

#### Update `gateway-site.xml` on Knox host ####

Update `conf/gateway-site.xml` in your Knox installation and set the value of `gateway.hadoop.kerberos.secured` to true.

#### Restart Knox ####

After you do the above configurations and restart Knox, Knox would use SPNEGO to authenticate with Hadoop services and Oozie.
There is no change in the way you make calls to Knox whether you use curl or Knox DSL.
