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

## Troubleshooting ##

### Finding Logs ###

When things aren't working the first thing you need to do is examine the diagnostic logs.
Depending upon how you are running the gateway these diagnostic logs will be output to different locations.

#### java -jar bin/gateway.jar ####

When the gateway is run this way the diagnostic output is written directly to the console.
If you want to capture that output you will need to redirect the console output to a file using OS specific techniques.

    java -jar bin/gateway.jar > gateway.log

#### bin/gateway.sh start ####

When the gateway is run this way the diagnostic output is written to `{GATEWAY_HOME}/log/knox.out` and `{GATEWAY_HOME}/log/knox.err`.
Typically only knox.out will have content.


### Increasing Logging ###

The `log4j.properties` files `{GATEWAY_HOME}/conf` can be used to change the granularity of the logging done by Knox.
The Knox server must be restarted in order for these changes to take effect.
There are various useful loggers pre-populated but commented out.

    log4j.logger.org.apache.knox.gateway=DEBUG # Use this logger to increase the debugging of Apache Knox itself.
    log4j.logger.org.apache.shiro=DEBUG          # Use this logger to increase the debugging of Apache Shiro.
    log4j.logger.org.apache.http=DEBUG           # Use this logger to increase the debugging of Apache HTTP components.
    log4j.logger.org.apache.http.client=DEBUG    # Use this logger to increase the debugging of Apache HTTP client component.
    log4j.logger.org.apache.http.headers=DEBUG   # Use this logger to increase the debugging of Apache HTTP header.
    log4j.logger.org.apache.http.wire=DEBUG      # Use this logger to increase the debugging of Apache HTTP wire traffic.


### LDAP Server Connectivity Issues ###

If the gateway cannot contact the configured LDAP server you will see errors in the gateway diagnostic output.

    13/11/15 16:30:17 DEBUG authc.BasicHttpAuthenticationFilter: Attempting to execute login with headers [Basic Z3Vlc3Q6Z3Vlc3QtcGFzc3dvcmQ=]
    13/11/15 16:30:17 DEBUG ldap.JndiLdapRealm: Authenticating user 'guest' through LDAP
    13/11/15 16:30:17 DEBUG ldap.JndiLdapContextFactory: Initializing LDAP context using URL 	[ldap://localhost:33389] and principal [uid=guest,ou=people,dc=hadoop,dc=apache,dc=org] with pooling disabled
    13/11/15 16:30:17 DEBUG servlet.SimpleCookie: Added HttpServletResponse Cookie [rememberMe=deleteMe; Path=/gateway/vaultservice; Max-Age=0; Expires=Thu, 14-Nov-2013 21:30:17 GMT]
    13/11/15 16:30:17 DEBUG authc.BasicHttpAuthenticationFilter: Authentication required: sending 401 Authentication challenge response.

The client should see something along the lines of:

    HTTP/1.1 401 Unauthorized
    WWW-Authenticate: BASIC realm="application"
    Content-Length: 0
    Server: Jetty(8.1.12.v20130726)

Resolving this will require ensuring that the LDAP server is running and that connection information is correct.
The LDAP server connection information is configured in the cluster's topology file (e.g. {GATEWAY_HOME}/deployments/sandbox.xml).


### Hadoop Cluster Connectivity Issues ###

If the gateway cannot contact one of the services in the configured Hadoop cluster you will see errors in the gateway diagnostic output.

    13/11/18 18:49:45 WARN knox.gateway: Connection exception dispatching request: http://localhost:50070/webhdfs/v1/?user.name=guest&op=LISTSTATUS org.apache.http.conn.HttpHostConnectException: Connection to http://localhost:50070 refused
    org.apache.http.conn.HttpHostConnectException: Connection to http://localhost:50070 refused
      at org.apache.http.impl.conn.DefaultClientConnectionOperator.openConnection(DefaultClientConnectionOperator.java:190)
      at org.apache.http.impl.conn.ManagedClientConnectionImpl.open(ManagedClientConnectionImpl.java:294)
      at org.apache.http.impl.client.DefaultRequestDirector.tryConnect(DefaultRequestDirector.java:645)
      at org.apache.http.impl.client.DefaultRequestDirector.execute(DefaultRequestDirector.java:480)
      at org.apache.http.impl.client.AbstractHttpClient.execute(AbstractHttpClient.java:906)
      at org.apache.http.impl.client.AbstractHttpClient.execute(AbstractHttpClient.java:805)
      at org.apache.http.impl.client.AbstractHttpClient.execute(AbstractHttpClient.java:784)
      at org.apache.knox.gateway.dispatch.HttpClientDispatch.executeRequest(HttpClientDispatch.java:99)

The resulting behavior on the client will differ by client.
For the client DSL executing the `{GATEWAY_HOME}/samples/ExampleWebHdfsLs.groovy` the output will look like this.

    Caught: org.apache.knox.gateway.shell.HadoopException: org.apache.knox.gateway.shell.ErrorResponse: HTTP/1.1 500 Server Error
    org.apache.knox.gateway.shell.HadoopException: org.apache.knox.gateway.shell.ErrorResponse: HTTP/1.1 500 Server Error
      at org.apache.knox.gateway.shell.AbstractRequest.now(AbstractRequest.java:72)
      at org.apache.knox.gateway.shell.AbstractRequest$now.call(Unknown Source)
      at ExampleWebHdfsLs.run(ExampleWebHdfsLs.groovy:28)

When executing commands requests via cURL the output might look similar to the following example.

    Set-Cookie: JSESSIONID=16xwhpuxjr8251ufg22f8pqo85;Path=/gateway/sandbox;Secure
    Content-Type: text/html;charset=ISO-8859-1
    Cache-Control: must-revalidate,no-cache,no-store
    Content-Length: 21856
    Server: Jetty(8.1.12.v20130726)

    <html>
    <head>
    <meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
    <title>Error 500 Server Error</title>
    </head>
    <body><h2>HTTP ERROR 500</h2>

Resolving this will require ensuring that the Hadoop services are running and that connection information is correct.
Basic Hadoop connectivity can be evaluated using cURL as described elsewhere.
Otherwise the Hadoop cluster connection information is configured in the cluster's topology file (e.g. `{GATEWAY_HOME}/deployments/sandbox.xml`).

### HTTP vs HTTPS protocol issues ###
When Knox is configured to accept requests over SSL and is presented with a request over plain HTTP, the client is presented with an error such as seen in the following:

    curl -i -k -u guest:guest-password -X GET 'http://localhost:8443/gateway/sandbox/webhdfs/v1/?op=LISTSTATUS'
    the following error is returned
    curl: (52) Empty reply from server

This is the default behavior for Jetty SSL listener. While the credentials to the default authentication provider continue to be username and password, we do not want to encourage sending these in clear text. Since preemptively sending BASIC credentials is a common pattern with REST APIs it would be unwise to redirect to a HTTPS listener thus allowing clear text passwords.

To resolve this issue, we have two options:

1. change the scheme in the URL to https and deal with any trust relationship issues with the presented server certificate
2. Disabling SSL in gateway-site.xml - this is not encouraged due to the reasoning described above.

### Check Hadoop Cluster Access via cURL ###

When you are experiencing connectivity issue it can be helpful to "bypass" the gateway and invoke the Hadoop REST APIs directly.
This can easily be done using the cURL command line utility or many other REST/HTTP clients.
Exactly how to use cURL depends on the configuration of your Hadoop cluster.
In general however you will use a command line the one that follows.

    curl -ikv -X GET 'http://namenode-host:50070/webhdfs/v1/?op=LISTSTATUS'

If you are using Sandbox the WebHDFS or NameNode port will be mapped to localhost so this command can be used.

    curl -ikv -X GET 'http://localhost:50070/webhdfs/v1/?op=LISTSTATUS'

If you are using a cluster secured with Kerberos you will need to have used `kinit` to authenticate to the KDC.
Then the command below should verify that WebHDFS in the Hadoop cluster is accessible.

    curl -ikv --negotiate -u : -X 'http://localhost:50070/webhdfs/v1/?op=LISTSTATUS'


### Authentication Issues ###
The following log information is available when you enable debug level logging for shiro. This can be done within the conf/log4j.properties file. Not the "Password not correct for user" message.

    13/11/15 16:37:15 DEBUG authc.BasicHttpAuthenticationFilter: Attempting to execute login with headers [Basic Z3Vlc3Q6Z3Vlc3QtcGFzc3dvcmQw]
    13/11/15 16:37:15 DEBUG ldap.JndiLdapRealm: Authenticating user 'guest' through LDAP
    13/11/15 16:37:15 DEBUG ldap.JndiLdapContextFactory: Initializing LDAP context using URL [ldap://localhost:33389] and principal [uid=guest,ou=people,dc=hadoop,dc=apache,dc=org] with pooling disabled
    2013-11-15 16:37:15,899 INFO  Password not correct for user 'uid=guest,ou=people,dc=hadoop,dc=apache,dc=org'
    2013-11-15 16:37:15,899 INFO  Authenticator org.apache.directory.server.core.authn.SimpleAuthenticator@354c78e3 failed to authenticate: BindContext for DN 'uid=guest,ou=people,dc=hadoop,dc=apache,dc=org', credentials <0x67 0x75 0x65 0x73 0x74 0x2D 0x70 0x61 0x73 0x73 0x77 0x6F 0x72 0x64 0x30 >
    2013-11-15 16:37:15,899 INFO  Cannot bind to the server
    13/11/15 16:37:15 DEBUG servlet.SimpleCookie: Added HttpServletResponse Cookie [rememberMe=deleteMe; Path=/gateway/vaultservice; Max-Age=0; Expires=Thu, 14-Nov-2013 21:37:15 GMT]
    13/11/15 16:37:15 DEBUG authc.BasicHttpAuthenticationFilter: Authentication required: sending 401 Authentication challenge response.

The client will likely see something along the lines of:

    HTTP/1.1 401 Unauthorized
    WWW-Authenticate: BASIC realm="application"
    Content-Length: 0
    Server: Jetty(8.1.12.v20130726)

#### Using ldapsearch to verify LDAP connectivity and credentials

If your authentication to Knox fails and you believe you're using correct credentials, you could try to verify the connectivity and credentials using ldapsearch, assuming you are using LDAP directory for authentication.

Assuming you are using the default values that came out of box with Knox, your ldapsearch command would be like the following

    ldapsearch -h localhost -p 33389 -D "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org" -w guest-password -b "uid=guest,ou=people,dc=hadoop,dc=apache,dc=org" "objectclass=*"

This should produce output like the following

    # extended LDIF
    
    LDAPv3
    base <uid=guest,ou=people,dc=hadoop,dc=apache,dc=org> with scope subtree
    filter: objectclass=*
    requesting: ALL
    
    
    # guest, people, hadoop.apache.org
    dn: uid=guest,ou=people,dc=hadoop,dc=apache,dc=org
    objectClass: organizationalPerson
    objectClass: person
    objectClass: inetOrgPerson
    objectClass: top
    uid: guest
    cn: Guest
    sn: User
    userpassword:: Z3Vlc3QtcGFzc3dvcmQ=
    
    # search result
    search: 2
    result: 0 Success
    
    # numResponses: 2
    # numEntries: 1

In a more general form the ldapsearch command would be

    ldapsearch -h {HOST} -p {PORT} -D {DN of binding user} -w {bind password} -b {DN of binding user} "objectclass=*}

### Hostname Resolution Issues ###

The deployments/sandbox.xml topology file has the host mapping feature enabled.
This is required due to the way networking is setup in the Sandbox VM.
Specifically the VM's internal hostname is sandbox.hortonworks.com.
Since this hostname cannot be resolved to the actual VM Knox needs to map that hostname to something resolvable.

If for example host mapping is disabled but the Sandbox VM is still used you will see an error in the diagnostic output similar to the below.

    13/11/18 19:11:35 WARN knox.gateway: Connection exception dispatching request: http://sandbox.hortonworks.com:50075/webhdfs/v1/user/guest/example/README?op=CREATE&namenoderpcaddress=sandbox.hortonworks.com:8020&user.name=guest&overwrite=false java.net.UnknownHostException: sandbox.hortonworks.com
    java.net.UnknownHostException: sandbox.hortonworks.com
      at java.net.Inet6AddressImpl.lookupAllHostAddr(Native Method)

On the other hand if you are migrating from the Sandbox based configuration to a cluster you have deployment you may see a similar error.
However in this case you may need to disable host mapping.
This can be done by modifying the topology file (e.g. deployments/sandbox.xml) for the cluster.

    ...
    <provider>
        <role>hostmap</role>
        <name>static</name>
        <enabled>false</enabled>
        <param><name>localhost</name><value>sandbox,sandbox.hortonworks.com</value></param>
    </provider>
    ....


### Job Submission Issues - HDFS Home Directories ###

If you see error like the following in your console  while submitting a Job using groovy shell, it is likely that the authenticated user does not have a home directory on HDFS.

    Caught: org.apache.knox.gateway.shell.HadoopException: org.apache.knox.gateway.shell.ErrorResponse: HTTP/1.1 403 Forbidden
    org.apache.knox.gateway.shell.HadoopException: org.apache.knox.gateway.shell.ErrorResponse: HTTP/1.1 403 Forbidden

You would also see this error if you try file operation on the home directory of the authenticating user.

The error would look a little different as shown below  if you are attempting to the operation with cURL.

    {"RemoteException":{"exception":"AccessControlException","javaClassName":"org.apache.hadoop.security.AccessControlException","message":"Permission denied: user=tom, access=WRITE, inode=\"/user\":hdfs:hdfs:drwxr-xr-x"}}* 

#### Resolution

Create the home directory for the user on HDFS.
The home directory is typically of the form `/user/{userid}` and should be owned by the user.
user 'hdfs' can create such a directory and make the user owner of the directory.


### Job Submission Issues - OS Accounts ###

If the Hadoop cluster is not secured with Kerberos, the user submitting a job need not have an OS account on the Hadoop NodeManagers.

If the Hadoop cluster is secured with Kerberos, the user submitting the job should have an OS account on Hadoop NodeManagers.

In either case if the user does not have such OS account, his file permissions are based on user ownership of files or "other" permission in "ugo" posix permission.
The user does not get any file permission as a member of any group if you are using default `hadoop.security.group.mapping`.

TODO: add sample error message from running test on secure cluster with missing OS account

### HBase Issues ###

If you experience problems running the HBase samples with the Sandbox VM it may be necessary to restart HBase and the HBASE REST API.
This can sometimes occur with the Sandbox VM is restarted from a saved state.
If the client hangs after emitting the last line in the sample output below you are most likely affected.

    System version : {...}
    Cluster version : 0.96.0.2.0.6.0-76-hadoop2
    Status : {...}
    Creating table 'test_table'...

HBase and the HBASE REST API can be restarted using the following commands on the Hadoop Sandbox VM.
You will need to ssh into the VM in order to run these commands.

    sudo -u hbase /usr/lib/hbase/bin/hbase-daemon.sh stop master
    sudo -u hbase /usr/lib/hbase/bin/hbase-daemon.sh start master
    sudo -u hbase /usr/lib/hbase/bin/hbase-daemon.sh restart rest


### SSL Certificate Issues ###

Clients that do not trust the certificate presented by the server will behave in different ways.
A browser will typically warn you of the inability to trust the received certificate and give you an opportunity to add an exception for the particular certificate.
Curl will present you with the follow message and instructions for turning of certificate verification:

    curl performs SSL certificate verification by default, using a "bundle" 
     of Certificate Authority (CA) public keys (CA certs). If the default
     bundle file isn't adequate, you can specify an alternate file
     using the --cacert option.
    If this HTTPS server uses a certificate signed by a CA represented 
     the bundle, the certificate verification probably failed due to a
     problem with the certificate (it might be expired, or the name might
     not match the domain name in the URL).
    If you'd like to turn off curl's verification of the certificate, use
     the -k (or --insecure) option.


### SPNego Authentication Issues ###

Calls from Knox to Secure Hadoop Cluster fails, with SPNego authentication problems,
if there was a TGT for Knox in disk cache when Knox was started.

You are likely to run into this situation on developer machines where the developer could have kinited for some testing.

Work Around: clear TGT of Knox from disk cache (calling `kdestroy` would do it), before starting Knox

### Filing Bugs ###

Bugs can be filed using [Jira][jira].
Please include the results of this command below in the Environment section.
Also include the version of Hadoop being used in the same section.

    cd {GATEWAY_HOME}
    java -jar bin/gateway.jar -version

