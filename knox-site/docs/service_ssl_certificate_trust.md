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

### TLS/SSL Certificate Trust ###

When the Gateway dispatches requests to a configured service using TLS/SSL, that service's certificate 
must be trusted inorder for the connection to succeed.  To do this, the Gateway checks 
a configured trust store for the service's certificate or the certificate of the CA that issued that 
certificate. 

If not explicitly set, the Gateway will use its configured identity keystore as the trust store.
By default, this keystore is located at `{GATEWAY_HOME}/data/security/keystores/gateway.jks`; however, 
a custom identity keystore may be set in the gateway-site.xml file. See `gateway.tls.keystore.password.alias`, `gateway.tls.keystore.path`, 
and `gateway.tls.keystore.type`. 
   
The trust store is configured at the Gatway-level.  There is no support to set a different trust store
per service. To use a specific trust store, the following configuration elements may be set in the 
gateway-site.xml file:

| Configuration Element                          | Description                                               |
| -----------------------------------------------|-----------------------------------------------------------|
| gateway.httpclient.truststore.path             | Fully qualified path to the trust store to use. Default is the keystore used to hold the Gateway's identity.  See `gateway.tls.keystore.path`.|
| gateway.httpclient.truststore.type             | Keystore type of the trust store. Default is JKS.         |
| gateway.httpclient.truststore.password.alias   | Alias for the password to the trust store.|


If `gateway.httpclient.truststore.path` is not set, the keystore used to hold the Gateway's identity 
will be used as the trust store. 

However, if `gateway.httpclient.truststore.path` is set, it is expected that 
`gateway.httpclient.truststore.type` and `gateway.httpclient.truststore.password.alias` are set
appropriately. If `gateway.httpclient.truststore.type` is not set, the Gateway will assume the trust 
store is a JKS file. If `gateway.httpclient.truststore.password.alias` is not set, the Gateway will
assume the alias name is "gateway-httpclient-truststore-password".  In any case, if the 
trust store password is different from the Gateway's master secret then it can be set using

    knoxcli.sh create-alias {password-alias} --value {pwd} 
  
If a password is not found using the provided (or default) alias name, then the Gateway's master secret 
will be used.

All topologies deployed within the Gateway instance will use the configured trust store to verify a 
service's identity.  
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

### TLS/SSL Certificate Trust ###

When the Gateway dispatches requests to a configured service using TLS/SSL, that service's certificate 
must be trusted inorder for the connection to succeed.  To do this, the Gateway checks 
a configured trust store for the service's certificate or the certificate of the CA that issued that 
certificate. 

If not explicitly set, the Gateway will use its configured identity keystore as the trust store.
By default, this keystore is located at `{GATEWAY_HOME}/data/security/keystores/gateway.jks`; however, 
a custom identity keystore may be set in the gateway-site.xml file. See `gateway.tls.keystore.password.alias`, `gateway.tls.keystore.path`, 
and `gateway.tls.keystore.type`. 
   
The trust store is configured at the Gatway-level.  There is no support to set a different trust store
per service. To use a specific trust store, the following configuration elements may be set in the 
gateway-site.xml file:

| Configuration Element                          | Description                                               |
| -----------------------------------------------|-----------------------------------------------------------|
| gateway.httpclient.truststore.path             | Fully qualified path to the trust store to use. Default is the keystore used to hold the Gateway's identity.  See `gateway.tls.keystore.path`.|
| gateway.httpclient.truststore.type             | Keystore type of the trust store. Default is JKS.         |
| gateway.httpclient.truststore.password.alias   | Alias for the password to the trust store.|


If `gateway.httpclient.truststore.path` is not set, the keystore used to hold the Gateway's identity 
will be used as the trust store. 

However, if `gateway.httpclient.truststore.path` is set, it is expected that 
`gateway.httpclient.truststore.type` and `gateway.httpclient.truststore.password.alias` are set
appropriately. If `gateway.httpclient.truststore.type` is not set, the Gateway will assume the trust 
store is a JKS file. If `gateway.httpclient.truststore.password.alias` is not set, the Gateway will
assume the alias name is "gateway-httpclient-truststore-password".  In any case, if the 
trust store password is different from the Gateway's master secret then it can be set using

    knoxcli.sh create-alias {password-alias} --value {pwd} 
  
If a password is not found using the provided (or default) alias name, then the Gateway's master secret 
will be used.

All topologies deployed within the Gateway instance will use the configured trust store to verify a 
service's identity.  
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

### TLS/SSL Certificate Trust ###

When the Gateway dispatches requests to a configured service using TLS/SSL, that service's certificate 
must be trusted inorder for the connection to succeed.  To do this, the Gateway checks 
a configured trust store for the service's certificate or the certificate of the CA that issued that 
certificate. 

If not explicitly set, the Gateway will use its configured identity keystore as the trust store.
By default, this keystore is located at `{GATEWAY_HOME}/data/security/keystores/gateway.jks`; however, 
a custom identity keystore may be set in the gateway-site.xml file. See `gateway.tls.keystore.password.alias`, `gateway.tls.keystore.path`, 
and `gateway.tls.keystore.type`. 
   
The trust store is configured at the Gatway-level.  There is no support to set a different trust store
per service. To use a specific trust store, the following configuration elements may be set in the 
gateway-site.xml file:

| Configuration Element                          | Description                                               |
| -----------------------------------------------|-----------------------------------------------------------|
| gateway.httpclient.truststore.path             | Fully qualified path to the trust store to use. Default is the keystore used to hold the Gateway's identity.  See `gateway.tls.keystore.path`.|
| gateway.httpclient.truststore.type             | Keystore type of the trust store. Default is JKS.         |
| gateway.httpclient.truststore.password.alias   | Alias for the password to the trust store.|


If `gateway.httpclient.truststore.path` is not set, the keystore used to hold the Gateway's identity 
will be used as the trust store. 

However, if `gateway.httpclient.truststore.path` is set, it is expected that 
`gateway.httpclient.truststore.type` and `gateway.httpclient.truststore.password.alias` are set
appropriately. If `gateway.httpclient.truststore.type` is not set, the Gateway will assume the trust 
store is a JKS file. If `gateway.httpclient.truststore.password.alias` is not set, the Gateway will
assume the alias name is "gateway-httpclient-truststore-password".  In any case, if the 
trust store password is different from the Gateway's master secret then it can be set using

    knoxcli.sh create-alias {password-alias} --value {pwd} 
  
If a password is not found using the provided (or default) alias name, then the Gateway's master secret 
will be used.

All topologies deployed within the Gateway instance will use the configured trust store to verify a 
service's identity.  
