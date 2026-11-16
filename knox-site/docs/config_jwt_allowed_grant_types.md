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

### JWT Provider Allowed Grant Types ###

By default the `JWTProvider` federation provider passes every OAuth 2.0 grant through to the token endpoint behind it, such as the [KnoxIDF](knoxidf/endpoints.md#token-endpoint) token endpoint. Setting `jwt.allowed.grant.types` restricts a topology's token endpoint to the listed grants:

    <provider>
        <role>federation</role>
        <name>JWTProvider</name>
        <enabled>true</enabled>
        <param>
            <name>jwt.allowed.grant.types</name>
            <value>urn:ietf:params:oauth:grant-type:token-exchange</value>
        </param>
    </provider>

Parameter | Description | Default
----------|-------------|--------
jwt.allowed.grant.types | Comma-separated `grant_type` values the topology's token endpoint accepts. | unset (every grant is accepted)

Once set:

*   A request naming any other `grant_type` is rejected with `400 unsupported_grant_type` before its credentials (client secret, `client_assertion`, refresh token or `subject_token`) are validated.
*   The rejection is audited as `event=token_grant grant_type=<grant> reason=grant_type_not_allowed`. A grant type KnoxIDF does not support is logged as `other`, so caller input never reaches the audit log.
*   The KnoxIDF token endpoint also rejects a request without a `grant_type`, so a bearer token cannot be used to mint a fresh token there.
*   The KnoxIDF discovery documents advertise only the listed grants in `grant_types_supported`. Without `authorization_code`, the `authorization_endpoint`, `registration_endpoint` and `userinfo_endpoint` are omitted too.

With the parameter set, the `JWTProvider` reads `grant_type` from the form body of every authenticated request. Set it only on token-endpoint topologies, not on topologies that proxy form posts to a backend.
