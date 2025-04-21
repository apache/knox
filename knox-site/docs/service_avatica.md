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

### Avatica ###

Knox provides gateway functionality for access to all Apache Avatica-based servers.
The gateway can be used to provide authentication and encryption for clients to
servers like the Apache Phoenix Query Server.

#### Gateway configuration ####

The Gateway can be configured for Avatica by modifying the topology XML file
and providing a new service XML file.

In the topology XML file, add the following with the correct hostname:

    <service>
      <role>AVATICA</role>
      <url>http://avatica:8765</url>
    </service>

Your installation likely already contains the following service files. Ensure
that they are present in your installation. In `services/avatica/1.9.0/rewrite.xml`:

    <rules>
        <rule dir="IN" name="AVATICA/avatica/inbound/root" pattern="*://*:*/**/avatica/">
            <rewrite template="{$serviceUrl[AVATICA]}/"/>
        </rule>
        <rule dir="IN" name="AVATICA/avatica/inbound/path" pattern="*://*:*/**/avatica/{**}">
            <rewrite template="{$serviceUrl[AVATICA]}/{**}"/>
        </rule>
    </rules>

And in `services/avatica/1.9.0/service.xml`:

    <service role="AVATICA" name="avatica" version="1.9.0">
        <policies>
            <policy role="webappsec"/>
            <policy role="authentication"/>
            <policy role="rewrite"/>
            <policy role="authorization"/>
        </policies>
        <routes>
            <route path="/avatica">
                <rewrite apply="AVATICA/avatica/inbound/root" to="request.url"/>
            </route>
            <route path="/avatica/**">
                <rewrite apply="AVATICA/avatica/inbound/path" to="request.url"/>
            </route>
        </routes>
    </service>

#### JDBC Drivers ####

In most cases, users only need to modify the hostname of the Avatica server to
instead be the Knox Gateway. To enable authentication, some of the Avatica
property need to be added to the Properties object used when constructing the
`Connection` or to the JDBC URL directly.

The JDBC URL can be modified like:

    jdbc:avatica:remote:url=https://knox_gateway.domain:8443/gateway/sandbox/avatica;avatica_user=username;avatica_password=password;authentication=BASIC

Or, using the `Properties` class:

    Properties props = new Properties();
    props.setProperty("avatica_user", "username");
    props.setProperty("avatica_password", "password");
    props.setProperty("authentication", "BASIC");
    DriverManager.getConnection(url, props);

Additionally, when the TLS certificate of the Knox Gateway is not trusted by your JVM installation,
it will be necessary for you to pass in a custom truststore and truststore password to perform the
necessary TLS handshake. This can be realized with the `truststore` and `truststore_password` properties
using the same approaches as above.

Via the JDBC URL:

    jdbc:avatica:remote:url=https://...;authentication=BASIC;truststore=/tmp/knox_truststore.jks;truststore_password=very_secret

Using Java code:

    ...
    props.setProperty("truststore", "/tmp/knox_truststore.jks");
    props.setProperty("truststore_password", "very_secret");
    DriverManager.getConnection(url, props);
