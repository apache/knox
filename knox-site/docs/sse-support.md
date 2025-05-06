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

## Server-Sent Events Support ##

### Introduction

Server-Sent Events (SSE) allows a server to push updates to the browser over a single longlived HTTP connection. The media type is test/event-stream.

### Configuration ###

Asynchronous behaviour and therefore SSE support is not enabled by default. Modify the `gateway.servlet.async.supported` property to `true` in `<KNOX-HOME>/conf/gateway-site.xml` file.

      <property>
          <name>gateway.servlet.async.supported</name>
          <value>true</value>
          <description>Enable/Disable async support.</description>
      </property>

Service and rewrite rules need the be updated as well. Both Ha and non Ha configurations work.

### Example ###

In the following sample configuration we assume that the backend SSE service URL is http://myhost:7435. And `gateway.servlet.async.supported` property is set to `true` as shown above.

### Rewrite ###

Example code snippet from `<KNOX-HOME>/data/services/{myservice}/{version}/rewrite.xml` where myservice = sservice and version = 0.1

      <rules>
          <rule dir="IN" name="SSERVICE/sservice/inbound" pattern="*://*:*/**/sservice/*">
            <rewrite template="{$serviceUrl[SSERVICE]}/sse"/>
          </rule>
      </rules>

### Service ###

Example code snippet from `<KNOX-HOME>/data/services/{myservice}/{version}/service.xml` where myservice = sservice and version = 0.1

      <service role="SSERVICE" name="sservice" version="0.1">
        <routes>
          <route path="/sservice/**"></route>
        </routes>
        <dispatch classname="org.apache.knox.gateway.sse.SSEDispatch" ha-classname="org.apache.knox.gateway.ha.dispatch.SSEHaDispatch"/>
      </service>

### Topology ###

Finally, update the topology file at `<KNOX-HOME>/conf/{topology}.xml`  with the backend service URL

      <service>
        <role>SSERVICE</role>
        <url>http://myhost:7435</url>
      </service>
