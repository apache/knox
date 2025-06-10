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

# Client Credentials API

### Introduction
The CLIENTID API is an extension of the KnoxToken API that defaults certain configuration and translates the responses in
a way that supports the specific OAuth Client Credentials Flow use cases. This API is used to issue API Keys for use with
services like the Iceberg REST Catalog API.

The only difference from the KnoxToken API in the configuration are the parameter names. They must be prefixed with
"clientid." this is done to disambiguate the config from that of KnoxToken itself when they are colocated in the same
topology.

In addition, the default behavior differs in that the time-to-live or TTL defaults to "-1" which means that by default
the API Keys do not expire. It also differs in that the returned APIKeys are Passcode tokens and as such are by definition
server managed. Therefore, we default the server managed configuration to true for convenience and to reduce errors in
deployment.

**Client Credentials** - The example below shows the interaction with the APIKey API via curl and the response with default behavior.

    <service>
        <role>CLIENTID</role>
    </service>

In this deployment example the TTL is -1 by default which means it never expires and is not included in the response.

    $ curl -ivku guest:guest-password -X POST "https://localhost:8443/gateway/sandbox/clientid/api/v1/oauth/credentials"
    {"client_secret":"WXpOa1l6SmxPRFF0TmpOalpTMDBPREZpTFRobE5qY3RO....jpOems1T1RabU5qSXROREl4T1MwMFlUVTBMV0UyWlRVdFptTXlNek0xTjJWaVl6SXg=","client_id":"c3dc2e84-63ce-481b-8e67-75f754894f87"}

**Client Credentials** - The example below shows the interaction with the APIKey API via curl and the response.

In this deployment example the TTL is set to 74000 ms which is translated to seconds in the response.

    <service>
        <role>CLIENTID</role>
        <param>
            <name>clientid.knox.token.ttl</name>
            <value>74000</value>
        </param>
    </service>

    $ curl -ivku guest:guest-password -X POST "https://localhost:8443/gateway/sandbox/clientid/api/v1/oauth/credentials"
    {"client_secret":"WXpKaE1qRmlOR0V0TkRBMk5DMDBNelZsTFdFek16RXR....WTVaVFprOjpZelJsTlRJMFlXVXROMlEwTXkwME5EQTVMV0k1WWpJdFlqZ3pOR00xTmpsa01qUXg=","expires_in":74,"client_id":"c2a21b4a-4064-435e-a331-6d6858ef9e6d"}

Note that in both of the above responses that there is a client_id and the client_secret.
The key_id may be used in management operations of the API Key lifecycle by those with appropriate permissions to do so.