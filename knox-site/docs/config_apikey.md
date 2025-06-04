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
# API Key API

### Introduction
The APIKEY API is an extension of the KnoxToken API that defaults certain configuration and translates the responses in
a way that supports the specific API Key use cases. This API is used to issue API Keys for use with services like AI
Inferencing APIs such as OpenAI compatible APIs where an Authorization Bearer Token is expected and it is not a JWT with
expectations around expiry and cryptographic verification of the credentials.

The only difference from the KnoxToken API in the configuration are the parameter names. They must be prefixed with
"apikey." this is done to disambiguate the config from that of KnoxToken itself when they are colocated in the same
topology.

In addition, the default behavior differs in that the time-to-live or TTL defaults to "-1" which means that by default
the API Keys do not expire. It also differs in that the returned APIKeys are Passcode tokens and as such are by definition
server managed. Therefore, we default the server managed configuration to true for convenience and to reduce errors in
deployment.

**API Key** - The example below shows the interaction with the APIKey API via curl and the response with default behavior.

    <service>
        <role>APIKEY</role>
    </service>

In this deployment example the TTL is -1 by default which means it never expires and is not included in the response.

    $ curl -ivku guest:guest-password -X POST "https://localhost:8443/gateway/sandbox/apikey/api/v1/auth/key"
    {"key_id":"9c2d22fb-e28d-4495-aaae-d4103dada8d1","api_key":"T1dNeVpESXlabUl0WlRJNFpDMDBORGsxTFdGaFlX....R1F4OjpNMlV5WXpFeE56a3RZbVJtTXkwME1HTTJMVGxoTmpVdE9HWXdNbUZrTTJWa016UXo="}

**API Key** - The example below shows the interaction with the APIKey API via curl and the response.

In this deployment example the TTL is set to 74000 ms which is translated to seconds in the response.

    <service>
        <role>APIKEY</role>
        <param>
            <name>apikey.knox.token.ttl</name>
            <value>74000</value>
        </param>
    </service>

    $ curl -ivku guest:guest-password -X POST "https://localhost:8443/gateway/sandbox/apikey/api/v1/auth/key"
    {"key_id":"9c2d22fb-e28d-4495-aaae-d4103dada8d1","api_key":"T1dNeVpESXlabUl0WlRJNFpDMDBORGsxTFdGaFlX....R1F4OjpNMlV5WXpFeE56a3RZbVJtTXkwME1HTTJMVGxoTmpVdE9HWXdNbUZrTTJWa016UXo=","expires_in":74}

Note that in both of the above response that there is a key_id as well as the api_key. The api_key is intended to be used
as the API Key via Authorization Bearer Token in the invocations of APIs.

The key_id may be used in management operations of the API Key lifecycle by those with appropriate permissions to do so.
