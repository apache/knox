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

## WebSocket Support ##

### Introduction

WebSocket is a communication protocol that allows full duplex communication over a single TCP connection.
Knox provides out-of-the-box support for the WebSocket protocol, currently only text messages are supported.

### Configuration ###

By default WebSocket functionality is disabled, it can be easily enabled by changing the `gateway.websocket.feature.enabled` property to `true` in `<KNOX-HOME>/conf/gateway-site.xml` file.  

      <property>
          <name>gateway.websocket.feature.enabled</name>
          <value>true</value>
          <description>Enable/Disable websocket feature.</description>
      </property>

Service and rewrite rules need to changed accordingly to match the appropriate websocket context.

### Example ###

In the following sample configuration we assume that the backend WebSocket URL is ws://myhost:9999/ws. And 'gateway.websocket.feature.enabled' property is set to 'true' as shown above.

#### rewrite ####

Example code snippet from `<KNOX-HOME>/data/services/{myservice}/{version}/rewrite.xml` where myservice = websocket and version = 0.6.0

      <rules>
        <rule dir="IN" name="WEBSOCKET/ws/inbound" pattern="*://*:*/**/ws">
          <rewrite template="{$serviceUrl[WEBSOCKET]}/ws"/>
        </rule>
      </rules>

#### service ####

Example code snippet from `<KNOX-HOME>/data/services/{myservice}/{version}/service.xml` where myservice = websocket and version = 0.6.0

      <service role="WEBSOCKET" name="websocket" version="0.6.0">
        <policies>
              <policy role="webappsec"/>
              <policy role="authentication" name="Anonymous"/>
              <policy role="rewrite"/>
              <policy role="authorization"/>
        </policies>
        <routes>
          <route path="/ws">
              <rewrite apply="WEBSOCKET/ws/inbound" to="request.url"/>
          </route>
        </routes>
      </service>

#### topology ####

Finally, update the topology file at `<KNOX-HOME>/conf/{topology}.xml`  with the backend service URL

      <service>
          <role>WEBSOCKET</role>
          <url>ws://myhost:9999/ws</url>
      </service>
