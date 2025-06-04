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
## KnoxToken Configuration

### Introduction
---

The Knox Token Service enables the ability for clients to acquire the same JWT token that is used for KnoxSSO with WebSSO flows for UIs to be used for accessing REST APIs. By acquiring the token and setting it as a Bearer token on a request, a client is able to access REST APIs that are protected with the JWTProvider federation provider.

This section describes the overall setup requirements and options for KnoxToken service.

### KnoxToken service
The Knox Token Service configuration can be configured in any descriptor/topology, tailored to issue tokens to authenticated users, and constrain the usage of the tokens in a number of ways.

    "services": [
      {
        "name": "KNOXTOKEN",
        "params": {
          "knox.token.ttl": "36000000",
		  "knox.token.audiences": "tokenbased",
		  "knox.token.target.url": "https://localhost:8443/gateway/tokenbased",
          "knox.token.exp.server-managed": "false",
          "knox.token.renewer.whitelist": "admin",
		  "knox.token.exp.renew-interval": "86400000",
		  "knox.token.exp.max-lifetime": "604800000",
		  "knox.token.type": "JWT"
        }
      }
    ]

#### KnoxToken Configuration Parameters

Parameter                        | Description | Default    |
-------------------------------- |------------ |----------- |
knox.token.ttl                | This indicates the lifespan (milliseconds) of the token. Once it expires a new token must be acquired from KnoxToken service. The 36000000 in the topology above gives you 10 hrs. | 30000 (30 seconds) |
knox.token.audiences          | This is a comma-separated list of audiences to add to the JWT token. This is used to ensure that a token received by a participating application knows that the token was intended for use with that application. It is optional. In the event that an endpoint has expected audiences and they are not present the token must be rejected. In the event where the token has audiences and the endpoint has none expected then the token is accepted.| empty |
knox.token.target.url         | This is an optional configuration parameter to indicate the intended endpoint for which the token may be used. The KnoxShell token credential collector can pull this URL from a knoxtokencache file to be used in scripts. This eliminates the need to prompt for or hardcode endpoints in your scripts. | n/a |
knox.token.exp.server-managed | This is an optional configuration parameter to enable/disable server-managed token state, to support the associated token renewal and revocation APIs. | false |
knox.token.renewer.whitelist  | This is an optional configuration parameter to authorize the comma-separated list of users to invoke the associated token renewal and revocation APIs. |  |
knox.token.exp.renew-interval | This is an optional configuration parameter to specify the amount of time (milliseconds) to be added to a token's TTL when a renewal request is approved. | 86400000 (24 hours) |
knox.token.exp.max-lifetime   | This is an optional configuration parameter to specify the maximum allowed lifetime (milliseconds) of a token, after which renewal will not be permitted. | 604800000 (7 days) |
knox.token.type | If this is configured the generated JWT's header will have this value as the `typ` property |  |
knox.token.issuer  | This is an optional configuration parameter to specify the issuer of a token. | KNOXSSO |

Note that server-managed token state can be configured for all KnoxToken service deployments in gateway-site (see [gateway.knox.token.exp.server-managed](#gateway-server-configuration)). If it is configured at the gateway level, then the associated service parameter, if configured, will override the gateway configuration.

Adding the KnoxToken configuration shown above to a topology that is protected with the ShrioProvider is a very simple and effective way to expose an endpoint from which a Knox token can be requested. Once it is acquired it may be used to access resources at intended endpoints until it expires.

The following curl command can be used to acquire a token from the Knox Token service as configured in the sandbox topology:

    curl -ivku guest:guest-password https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token
    
Resulting in a JSON response that contains the token, the expiration and the optional target endpoint:

          `{"access_token":"eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJndWVzdCIsImF1ZCI6InRva2VuYmFzZWQiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNDg5OTQyMTg4fQ.bcqSK7zMnABEM_HVsm3oWNDrQ_ei7PcMI4AtZEERY9LaPo9dzugOg3PA5JH2BRF-lXM3tuEYuZPaZVf8PenzjtBbuQsCg9VVImuu2r1YNVJlcTQ7OV-eW50L6OTI0uZfyrFwX6C7jVhf7d7YR1NNxs4eVbXpS1TZ5fDIRSfU3MU","target_url":"https://localhost:8443/gateway/tokenbased","token_type":"Bearer ","expires_in":1489942188233}`

The following curl example shows how to add a bearer token to an Authorization header:

    curl -ivk -H "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJndWVzdCIsImF1ZCI6InRva2VuYmFzZWQiLCJpc3MiOiJLTk9YU1NPIiwiZXhwIjoxNDg5OTQyMTg4fQ.bcqSK7zMnABEM_HVsm3oWNDrQ_ei7PcMI4AtZEERY9LaPo9dzugOg3PA5JH2BRF-lXM3tuEYuZPaZVf8PenzjtBbuQsCg9VVImuu2r1YNVJlcTQ7OV-eW50L6OTI0uZfyrFwX6C7jVhf7d7YR1NNxs4eVbXpS1TZ5fDIRSfU3MU" https://localhost:8443/gateway/tokenbased/webhdfs/v1/tmp?op=LISTSTATUS

If you want tokens to include group membership informations, add a `knox.token.include.groups` query parameter to the URL.

    curl -u admin:admin-password -k "https://localhost:8443/gateway/homepage/knoxtoken/api/v1/token?knox.token.include.groups=true"

The response contains the token with the group information:

    {
          "sub": "admin",
          "jku": "https://localhost:8443/gateway/homepage/knoxtoken/api/v1/jwks.json",
          "kid": "oigA7mZCwA2d7oimQyUaB0oDAfhI-1Bjq9y1n-Mw_OU",
          "iss": "KNOXSSO",
          "exp": 1649777837,
          "knox.groups": [
            "admin-group2",
            "admin-group1"
          ],
          "managed.token": "true",
          "knox.id": "dfeb8979-7f00-4938-bbff-1bc7574bb53d"
    }

This feature is enabled by default. If you want to disable it, add the following configuration to the KNOXTOKEN service.

         <param>
            <name>knox.token.include.groups.allowed</name> <!-- default = true -->
            <value>false</value>
        </param>

#### KnoxToken Renewal, Revocation and Enable/Disable actions

The KnoxToken service supports the renewal and explicit revocation of tokens it has issued.
Support for both requires server-managed token state to be enabled with at least one renewer white-listed.

##### Renewal
 
    curl -ivku admin:admin-password -X PUT -d $TOKEN 'https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/renew'

The JSON responses include a flag indicating success or failure.

A successful result includes the updated expiration time.

	{
	  "renewed": "true",
	  "expires": "1584278311658"
	}

Error results include a message describing the reason for failure.

Invalid token

	{
	  "renewed": "false",
	  "error": "Unknown token: 9caf743e-1e0d-4708-a9ac-a684a576067c"
	}

Unauthorized caller

    {
      "renewed": "false",
      "error": "Caller (guest) not authorized to renew tokens."
    }

##### Revocation

    curl -ivku admin:admin-password -X DELETE -d $TOKEN 'https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/revoke'

The JSON responses include a flag indicating success or failure.

	{
	  "revoked": "true"
	}

Error results include a message describing the reason for the failure.

Invalid token

    {
      "revoked": "false",
      "error": "Unknown token: 9caf743e-1e0d-4708-a9ac-a684a576067c"
    }

Unauthorized caller

	{
	  "revoked": "false",
	  "error": "Caller (guest) not authorized to revoke tokens."
	}

KnoxSSO Cookies must not be revoked

    $ curl -iku admin:admin-password  -H "Content-Type: application/json" -d 'c236d20c-4a05-4cfa-b35e-2ba6dc451de0' -X DELETE https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/revoke
    HTTP/1.1 403 Forbidden
    Date: Fri, 09 Oct 2023 08:55:25 GMT
    Set-Cookie: KNOXSESSIONID=node03e9y0cy8giy31rh00xc1mrcfx0.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Thu, 05-Oct-2023 08:55:25 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 113

    {
      "revoked": "false",
      "error": "SSO cookie (c236d20c...2ba6dc451de0) cannot not be revoked.",
      "code": 20
    }

Revoke multiple tokens in one batch:

    $ curl -iku admin:admin-password -H "Content-Type: application/json" -d '["3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","5735f5ae-bddd-4ed1-9383-47a839b9ae2b"]' -X DELETE https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/revokeTokens
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:20:39 GMT
    ...

    {
      "revoked": "true"
    }
    
When revoking multiple tokens, current token state check is executed one by one. This means, if there was at least failed token revocation, the HTTP response will indicate that despite the fact that the rest of the token revocation actions succeeded.

##### Enable

This endpoint added in the scope of [KNOX-2602](https://issues.apache.org/jira/browse/KNOX-2602).

    $ curl -ku admin:admin-password -d "1e2f286e-9df1-4123-8d41-e6af523d6923" -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/enable
    
    {
      "setEnabledFlag": "true",
      "isEnabled": "true"
    }
The JSON responses include a flag (`setEnabledFlag`) indicating success or failure along with the token state after the action is executed (`isEnabled`).

Trying to enable an already enabled token:

    $ curl -ku admin:admin-password -d "1e2f286e-9df1-4123-8d41-e6af523d6923" -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/enable
    {
      "setEnabledFlag": "false",
      "error": "Token is already enabled"
    }

Disabled KnoxSSO Cookies must not be (re-)enabled:

    $ curl -iku admin:admin-password  -H "Content-Type: application/json" -d '107824ab-c54d-4db3-b3b5-5c964892ad05' -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/enable
    HTTP/1.1 400 Bad Request
    Date: Fri, 06 Oct 2023 08:57:58 GMT
    Set-Cookie: KNOXSESSIONID=node011ejmvgcjnlpl13mchqmqjtdjc1.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Thu, 05-Oct-2023 08:57:58 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 107
    
    {
      "setEnabledFlag": "false",
      "error": "Disabled KnoxSSO Cookies cannot not be enabled",
      "code": 80
    }

Enable multiple tokens in one batch:

    $ curl -iku admin:admin-password -H "Content-Type: application/json" -d '["3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","5735f5ae-bddd-4ed1-9383-47a839b9ae2b"]' -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/enableTokens
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:19:23 GMT
    ...

    {
      "setEnabledFlag": "true",
      "isEnabled": "true"
    }
When enabling multiple tokens, current token state check is not executed. This means, if you are enabling tokens that were already enabled before the batch operation, they remain enabled.

##### Disable

This endpoint added in the scope of [KNOX-2602](https://issues.apache.org/jira/browse/KNOX-2602).

    $ curl -ku admin:admin-password -d "1e2f286e-9df1-4123-8d41-e6af523d6923" -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/disable
    {
      "setEnabledFlag": "true",
      "isEnabled": "false"
    }
The JSON responses include a flag (`setEnabledFlag`) indicating success or failure along with the token state after the action is executed (`isEnabled`).

Trying to enable an already enabled token:

    $ curl -ku admin:admin-password -d "1e2f286e-9df1-4123-8d41-e6af523d6923" -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/disable
    {
      "setEnabledFlag": "false",
      "error": "Token is already disabled"
    }

Disable multiple tokens in one batch:

    $ curl -iku admin:admin-password -H "Content-Type: application/json" -d '["3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","5735f5ae-bddd-4ed1-9383-47a839b9ae2b"]' -X PUT https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/disableTokens
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:16:14 GMT
    ...

    {
      "setEnabledFlag": "true",
      "isEnabled": "false"
    }
When disabling multiple tokens, current token state check is not executed. This means, if you are disabling tokens that were already disabled before the batch operation, they remain disabled.

##### Fetching tokens for users

Fetching tokens by `userName`:

    $ curl -iku admin:admin-password -X GET https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/getUserTokens?userName=admin
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:02:32 GMT
    Set-Cookie: KNOXSESSIONID=node01vfrmf5kpjt0ku6mt9765wwx64.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Mon, 09-Oct-2023 07:02:32 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 822

    {"tokens":[{"tokenId":"5244358f-19a3-4834-b16f-aa7ddb2e7fe1","issueTime":"2023-10-10T09:02:03.904+0200","expiration":"2023-10-11T09:02:03.000+0200","maxLifetime":"2023-10-17T09:02:03.904+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":true,"createdBy":null,"userName":"admin","enabled":true,"comment":null},"issueTimeLong":1696921323904,"expirationLong":1697007723000,"maxLifetimeLong":1697526123904},{"tokenId":"9b37e838-4aa2-43fd-b2f1-b35660b33778","issueTime":"2023-10-10T09:02:14.271+0200","expiration":"2023-10-10T10:02:14.242+0200","maxLifetime":"2023-10-17T09:02:14.271+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":null,"userName":"admin","enabled":true,"comment":"admin token 1"},"issueTimeLong":1696921334271,"expirationLong":1696924934242,"maxLifetimeLong":1697526134271}]}

Fetching tokens by `createdBy`:

    $ curl -iku admin:admin-password -X GET https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/getUserTokens?createdBy=admin
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:05:45 GMT
    Set-Cookie: KNOXSESSIONID=node047nn0zjkauc41qzexnjmlhj2j6.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Mon, 09-Oct-2023 07:05:46 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 436

    {"tokens":[{"tokenId":"3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","issueTime":"2023-10-10T09:02:29.146+0200","expiration":"2023-10-10T10:02:29.127+0200","maxLifetime":"2023-10-17T09:02:29.146+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":"admin","userName":"guest","enabled":true,"comment":"admin token 1 for guest"},"issueTimeLong":1696921349146,"expirationLong":1696924949127,"maxLifetimeLong":1697526149146}]}

Fetching tokens by `userNameOrCreatedBy`:

    $ curl -iku admin:admin-password -X GET https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/getUserTokens?userNameOrCreatedBy=admin
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:07:02 GMT
    Set-Cookie: KNOXSESSIONID=node0rt50pq4getaj1s1owcj3pvgfm7.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Mon, 09-Oct-2023 07:07:02 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 1246

    {"tokens":[{"tokenId":"5244358f-19a3-4834-b16f-aa7ddb2e7fe1","issueTime":"2023-10-10T09:02:03.904+0200","expiration":"2023-10-11T09:02:03.000+0200","maxLifetime":"2023-10-17T09:02:03.904+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":true,"createdBy":null,"userName":"admin","enabled":true,"comment":null},"issueTimeLong":1696921323904,"expirationLong":1697007723000,"maxLifetimeLong":1697526123904},{"tokenId":"9b37e838-4aa2-43fd-b2f1-b35660b33778","issueTime":"2023-10-10T09:02:14.271+0200","expiration":"2023-10-10T10:02:14.242+0200","maxLifetime":"2023-10-17T09:02:14.271+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":null,"userName":"admin","enabled":true,"comment":"admin token 1"},"issueTimeLong":1696921334271,"expirationLong":1696924934242,"maxLifetimeLong":1697526134271},{"tokenId":"3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","issueTime":"2023-10-10T09:02:29.146+0200","expiration":"2023-10-10T10:02:29.127+0200","maxLifetime":"2023-10-17T09:02:29.146+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":"admin","userName":"guest","enabled":true,"comment":"admin token 1 for guest"},"issueTimeLong":1696921349146,"expirationLong":1696924949127,"maxLifetimeLong":1697526149146}]}

Fetching `all` tokens:

    $ curl -iku admin:admin-password -X GET https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/getUserTokens?allTokens=true
    HTTP/1.1 200 OK
    Date: Tue, 10 Oct 2023 07:08:08 GMT
    Set-Cookie: KNOXSESSIONID=node0fctcnhp9fm3w1gq1mc2z993109.node0; Path=/gateway/sandbox; Secure; HttpOnly
    Expires: Thu, 01 Jan 1970 00:00:00 GMT
    Set-Cookie: rememberMe=deleteMe; Path=/gateway/sandbox; Max-Age=0; Expires=Mon, 09-Oct-2023 07:08:08 GMT; SameSite=lax
    Content-Type: application/json
    Content-Length: 2048

    {"tokens":[{"tokenId":"5244358f-19a3-4834-b16f-aa7ddb2e7fe1","issueTime":"2023-10-10T09:02:03.904+0200","expiration":"2023-10-11T09:02:03.000+0200","maxLifetime":"2023-10-17T09:02:03.904+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":true,"createdBy":null,"userName":"admin","enabled":true,"comment":null},"issueTimeLong":1696921323904,"expirationLong":1697007723000,"maxLifetimeLong":1697526123904},{"tokenId":"9b37e838-4aa2-43fd-b2f1-b35660b33778","issueTime":"2023-10-10T09:02:14.271+0200","expiration":"2023-10-10T10:02:14.242+0200","maxLifetime":"2023-10-17T09:02:14.271+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":null,"userName":"admin","enabled":true,"comment":"admin token 1"},"issueTimeLong":1696921334271,"expirationLong":1696924934242,"maxLifetimeLong":1697526134271},{"tokenId":"3c043de7-f9e9-4c1a-b32f-abfbc3dcbcb2","issueTime":"2023-10-10T09:02:29.146+0200","expiration":"2023-10-10T10:02:29.127+0200","maxLifetime":"2023-10-17T09:02:29.146+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":"admin","userName":"guest","enabled":true,"comment":"admin token 1 for guest"},"issueTimeLong":1696921349146,"expirationLong":1696924949127,"maxLifetimeLong":1697526149146},{"tokenId":"75f1b921-680d-433d-976f-270a100a1cf9","issueTime":"2023-10-10T09:07:50.871+0200","expiration":"2023-10-11T09:07:50.000+0200","maxLifetime":"2023-10-17T09:07:50.871+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":true,"createdBy":null,"userName":"sam","enabled":true,"comment":null},"issueTimeLong":1696921670871,"expirationLong":1697008070000,"maxLifetimeLong":1697526470871},{"tokenId":"5735f5ae-bddd-4ed1-9383-47a839b9ae2b","issueTime":"2023-10-10T09:07:55.293+0200","expiration":"2023-10-10T10:07:55.276+0200","maxLifetime":"2023-10-17T09:07:55.293+0200","metadata":{"customMetadataMap":{},"knoxSsoCookie":false,"createdBy":null,"userName":"sam","enabled":true,"comment":"sam token"},"issueTimeLong":1696921675293,"expirationLong":1696925275276,"maxLifetimeLong":1697526475293}]}


#### Token Generation/Management UIs

##### Overview

In Apache Knox v2.0.0 the team added two new UIs that are directly accessible from the Knox Home page:

*   Token Generation
*   Token Management

By default, the `homepage` topology comes with the `KNOXTOKEN` service enabled with the following attributes:

*   token TTL is set to 120 days
*   token service is enabled (default to keystore-based token state service)
*   the admin user is allowed to renew/revoke tokens

In this topology, homepage, two new applications were added in order to display the above-listed UIs:

*   `tokengen`: this is an old-style JSP UI, with a relatively simple JS code included. The source is located in the [gateway-applications](https://github.com/apache/knox/tree/v2.0.0/gateway-applications/src/main/resources/applications/tokengen) Maven sub-module.
*   `token-management`: this is an Angular UI. The source is located in its own [knox-token-management-ui](https://github.com/apache/knox/tree/v2.0.0/knox-token-management-ui) Maven sub-module.

On the Knox Home page, you will see a new town in the General Proxy Information table like this:

 ![Knox Token Management Homepage](assets/images/token/knoxtokenmanagement_homepage.png)

However, the _Integration Token_ links are disabled by default, because token integration requires a gateway-level alias - called `knox.token.hash.key` - being created and without that alias, it [does not make sense to show those links](https://github.com/apache/knox/pull/512).

##### Creating the token hash key

As explained, if you would like to use Knox's token generation features, you will have to create a gateway-level alias with a 256, 384, or 512-bit length JWK. You can do it in - at least - two different ways:

1.  You generate your own MAC (using [this online tool](https://8gwifi.org/jwkfunctions.jsp) for instance) and save it as an alias using Knox CLI.
2.  You do it running the following Knox CLI command:  
    `generate-jwk --saveAlias knox.token.hash.key`

The second option involves a newly created Knox CLI command called `generate-jwk`:

##### Token state service implementations

There was an important step the Knox team made to provide more flexibility for our end-users: there are some internal service implementations in Knox that were hard-coded in the Java source code. One of those services is the `Token State` service implementation which you can change in gateway-site.xml going forward by setting the `gateway.service.tokenstate.impl` property to any of:

1.  `org.apache.knox.gateway.services.token.impl.DefaultTokenStateService` - keeps all token information in memory, therefore all of this information is lost when Knox is shut down
2.  `org.apache.knox.gateway.services.token.impl.AliasBasedTokenStateService` - token information is stored in the gateway credential store. This is a durable option, but not suitable for HA deployments
3.  `org.apache.knox.gateway.services.token.impl.JournalBasedTokenStateService` - token information is stored in plain files within `$KNOX_DATA_DIR/security/token-state` folder. This option also provides a durable persistence layer for tokens and it might be good for HA scenarios too (in case of KNOX_DATA_DIR is on a shared drive), but the token data is written out in plain text (i.e. not encrypted) so it's less secure.
4.  `org.apache.knox.gateway.services.token.impl.ZookeeperTokenStateService` - this is an extension of the keystore-based approach. In this case, token information is stored in Zookeeper using Knox aliases. The token's alias name equals to its generated token ID.
5.  `org.apache.knox.gateway.services.token.impl.JDBCTokenStateService` - stores token information in relational databases. It's not only durable, but it's perfectly fine with HA deployments. Currently, PostgreSQL and MySQL databases are supported.

By default, the `AliasBasedTokenStateService` implementation is used.

##### Configuring the JDBC token state service

If you want to use the newly implemented database token management, you’ve to set `gateway.service.tokenstate.impl` in _gateway-site.xml_ to `org.apache.knox.gateway.services.token.impl.JDBCTokenStateService`.

Now, that you have configured your token state backend, you need to configure a valid database in _gateway-site.xml_. There are two ways to do that:

1.  You either declare database connection properties one-by-one:  
    `gateway.database.type` - should be set to `postgresql` or `mysql`  
    `gateway.database.host` - the host where your DB server is running  
    `gateway.database.port` - the port that your DB server is listening on  
    `gateway.database.name` - the name of the database you are connecting to
2.  Or you declare an all-in-one JDBC connection string called `gateway.database.connection.url`. The following value will show you how to connect to an SSL enabled PostgreSQL server:  
    <code>jdbc:postgresql://$myPostgresServerHost:5432/postgres?user=postgres&amp;ssl=true&amp;sslmode=verify-full&amp;sslrootcert=/usr/local/var/postgresql@10/data/root.crt</code>

If your database requires user/password authentication, the following aliases must be saved into the Knox Gateway’s credential store (__gateway-credentials.jceks):

*   `gateway_database_user` - the username
*   `gateway_database_password` - the password

###### Database design

 ![JDBC Token State Service Database Design](assets/images/token/jdbc_tss_db_design.png)

As you can see, there are only 2 tables:

 *   `KNOXTOKENS` contains basic information about the generated token
 *   `KNOX_TOKEN_METADATA` contains an arbitrary number of metadata information for the generated token. At the time of this document being written the following metadata exist:
	 * `passcode` - this is the BASE-64 encoded value of the generated
   passcode token MAC. That is, the BASE-64 decoded value is a generated
   MAC.
	 * `userName` - the logged-in user who generated the token
	 * `enabled` - this is a boolean flag indicating that the given token is enabled or not (a _disabled_ token cannot be used for
   authentication purposes)
	 * `comment` - this is optional metadata, saved only if the user enters something in the _Comment_ input field on the _Token
   Generation_ page (see below)
     * `createdBy` - if the token is an impersonated token, this metadata holds the name if the user who generated the token (see _Token impersonation_ below)

##### Generating a token

Once you configured the `knox.token.hash.key` alias and optionally customized your token state service, you are all set to generate Knox tokens using the new Token Generation UI:

 ![Token Generation UI](assets/images/token/knoxtokenmanagement_token_generation_ui-1.png)

The following sections are displayed on the page:

*   status bar: here you can see an informative message on the configured Token State backend. There are 3 different statuses:
    *   ERROR: shown in red. This indicates a problem with the service backend which makes the feature not work. Usually, this is visible when end-users configure JDBC token state service, but they make a mistake in their DB settings
    *   WARN: displayed in yellow (see above picture). This indicates that the feature is enabled and working, but there are some limitations
    *   INFO: displayed in green. This indicates when the token management backend is properly configured for HA and production deployments
*   there is an information label explaining the purpose of the token generation page
*   comment: this is an _optional_ input field that allows end-users to add meaningful comments (mnemonics) to their generated tokens. The maximum length is 255 characters.
*   the `Configured maximum lifetime` informs the clients about the `knox.token.ttl` property set in the `homepage` topology (defaults to 120 days). If that property is not set (e.g. someone removes it from he homepage topology), Knox uses a hard-coded value of 30 seconds (aka. default Knox token TTL)
*   Custom token lifetime can be set by adjusting the days/hours/minutes spinners. The default configuration will yield one hour.
*   Token impersonation: an optional free text input field that makes it possible to generate a token for someone else.
*   Clicking the Generate Token button will try to create a token for you.

##### About the generated token TTL

Out of the box, Knox will display the custom lifetime spinners on the Token Generation page. However, they can be hidden by setting the `knox.token.lifespan.input.enabled` property to `false` in the `homepage` topology. Given that possibility and the configured maximum lifetime the generated token can have the following TTL value:

*   there is no configured token TTL and lifespan inputs are disabled -> the default TTL is used (30 seconds)
*   there is configured TTL and lifespan inputs are disabled -> the configured TTL is used
*   there is configured TTL and lifespan inputs are enabled and lifespan inputs result in a value that is less than or equal to the configured TTL -> the lifespan query param is used
*   there is configured TTL and lifespan inputs are enabled and lifespan inputs result in a value that is greater than the configured TTL -> the configured TTL is used

##### Successful token generation

 ![Successful Token Generation](assets/images/token/knoxtokenmanagement_token_generation_ui-successful.png)

On the resulting page there is two sensitive information that you can use in Knox to authenticate your request:

1.  **JWT token** - this is the serialized JWT and is fully compatible with the old-style Bearer authorization method. Clicking the `JWT Token` label on the page will copy the value into the clipboard. You might want to use it as the ‘Token’ user:

    `$ curl -ku Token:eyJqa3UiOiJodHRwczpcL1wvbG9jYWxob3N0Ojg0NDNcL2dhdGV3YXlcL2hvbWVwYWdlXC9rbm94dG9rZW5cL2FwaVwvdjFcL2p3a3MuanNvbiIsImtpZCI6IkdsOTZfYTM2MTJCZWFsS2tURFRaOTZfVkVsLVhNRVRFRmZuNTRMQ1A2UDQiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImprdSI6Imh0dHBzOlwvXC9sb2NhbGhvc3Q6ODQ0M1wvZ2F0ZXdheVwvaG9tZXBhZ2VcL2tub3h0b2tlblwvYXBpXC92MVwvandrcy5qc29uIiwia2lkIjoiR2w5Nl9hMzYxMkJlYWxLa1REVFo5Nl9WRWwtWE1FVEVGZm41NExDUDZQNCIsImlzcyI6IktOT1hTU08iLCJleHAiOjE2MzY2MjU3MTAsIm1hbmFnZWQudG9rZW4iOiJ0cnVlIiwia25veC5pZCI6ImQxNjFjYWMxLWY5M2UtNDIyOS1hMGRkLTNhNzdhYjkxNDg3MSJ9.e_BNPf_G1iBrU0m3hul5VmmSbpw0w1pUAXl3czOcuxFOQ0Tki-Gq76fCBFUNdKt4QwLpNXxM321cH1TeMG4IhL-92QORSIZgRxY4OUtUgERzcU7-27VNYOzJbaRCjrx-Vb4bSriRJJDwbbXyAoEw_bjiP8EzFFJTPmGcctEzrOLWFk57cLO-2QLd2nbrNd4qmrRR6sEfP81Jg8UL-Ptp66vH_xalJJWuoyoNgGRmH8IMdLVwBgeLeVHiI7NmokuhO-vbctoEwV3Rt4pMpA0VSWGFN0MI4WtU0crjXXHg8U9xSZyOeyT3fMZBXctvBomhGlWaAvuT5AxQGyMMP3VLGw https:/localhost:8443/gateway/sandbox/webhdfs/v1?op=LISTSTATUS`
`{"FileStatuses":{"FileStatus":[{"accessTime":0,"blockSize":0,"childrenNum":1,"fileId":16386,"group":"supergroup","length":0,"modificationTime":1621238405734,"owner":"hdfs","pathSuffix":"tmp","permission":"1777","replication":0,"storagePolicy":0,"type":"DIRECTORY"},{"accessTime":0,"blockSize":0,"childrenNum":1,"fileId":16387,"group":"supergroup","length":0,"modificationTime":1621238326078,"owner":"hdfs","pathSuffix":"user","permission":"755","replication":0,"storagePolicy":0,"type":"DIRECTORY"}]}}`

2. **Passcode token** - this is the serialized passcode token, which you can use as the ‘Passcode’ user (Clicking the `Passcode Token` label on the page will copy the value into the clipboard):

    `$ curl -ku Passcode:WkRFMk1XTmhZekV0WmprelpTMDBNakk1TFdFd1pHUXRNMkUzTjJGaU9URTBPRGN4OjpPVEV5Tm1KbFltUXROVEUyWkMwME9HSTBMVGd4TTJZdE1HRmxaalJrWlRVNFpXRTA= https://localhost:8443/gateway/sandbox/webhdfs/v1?op=LISTSTATUS`
`{"FileStatuses":{"FileStatus":[{"accessTime":0,"blockSize":0,"childrenNum":1,"fileId":16386,"group":"supergroup","length":0,"modificationTime":1621238405734,"owner":"hdfs","pathSuffix":"tmp","permission":"1777","replication":0,"storagePolicy":0,"type":"DIRECTORY"},{"accessTime":0,"blockSize":0,"childrenNum":1,"fileId":16387,"group":"supergroup","length":0,"modificationTime":1621238326078,"owner":"hdfs","pathSuffix":"user","permission":"755","replication":0,"storagePolicy":0,"type":"DIRECTORY"}]}}`

The reason, we needed to support the shorter `Passcode token`, is that there are 3rd party tools where the long JWT exceeds input fields limitations so we need to address this issue with shorter token values.

The rest of the fields are complementary information such as the expiration date/time of the generated token or the user who created it.

##### Token generation failed

If there was an error during token generation, you will see a failure right under the input field boxes (above the Generate Token button):

 ![Failed Token Generation](assets/images/token/knoxtokenmanagement_token_generation_ui-fail.png)

The above error message indicates a failure that the admin user already generated more tokens than they are allowed to. This limitation is configurable in the `gateway-site.xml`:

- `gateway.knox.token.limit.per.user` - indicates the maximum number of tokens a user can manage at the same time. `-1` means that users are allowed to create/manage as many tokens as they want. This configuration only applies when the server-managed token state is enabled either in `gateway-site` or at the `topology` level. Defaults to 10.

This behavior can be changed by setting the following `KNOXTOKEN` service-level parameter called `knox.token.user.limit.exceeded.action`. This property may have the following values:

- `REMOVE_OLDEST` - if that’s configured, the oldest token of the user, who the token is being generated for, will be removed
- `RETURN_ERROR` - if that’s configured, Knox will return an error response with 403 error code (as it did in previous versions)

The default value is `RETURN_ERROR`.

##### Token Management

In addition to the token generation UI, Knox comes with the Token Management UI where logged-in users can see all the active tokens that were generated before. That is, if a token got expired and was removed from the underlying token store, it won't be displayed here. Based on a configuration you can find below, users can see only their tokens or all of them.

 ![Token Management UI](assets/images/token/knoxtokenmanagement_token_management_ui-1.png)

On this page, you will a table with the following information:

1. Each row starts with a selection checkbox for batch operations (except for disabled KnoxSSO cookies, as there is no point in doing anything with them)
2. A unique token identifer. Disabled token's Token ID value is shown in orange
3. Information on when the token was created and when it will expire
    1. if the token is already expired, the expiration time is shown in red
    2. if the token is still valid, the expiration time is shown in green
4. Username indicates the user for whom the token is created for
5. Impersonated is a boolean flag indicating if this is an impersonated token:
    1. green check: yes, this is impersonated. You'll see the user who created the token under the icon
    2. red cross: no, this is not an impersonated token
6. KnoxSSO is another boolean flag that indicates if this token is created by the `KNOXSSO` service if the feature was enabled
    1. green check: yes, this is KnoxSSO cookie (token)
    2. red cross: no, this is not a KnoxSSO cookie (it was created by a regular token API call or on the Token Generation page)
7. In the Actions column you will see
    1. the enable/disable/revoke actions are visible for impersonated tokens too
    2. KnoxSSO cookies cannot be revoked nor re-enabled

In order to refresh the table, you can use the `Refresh icon` above the table (if you generated tokens on another tab for instance).

**Batch operations**

When at least one token is selected, the following buttons are shown under the table:

- `Disable Selected Tokens`: when executed, all the selected tokens become disabled (if they were disabled originally, they will remain disabled)
- `Enable Selected Tokens`: when executed, all the selected tokens become enabled (if they were enabled originally, they will remain enabled)
- `Revoke Selected Tokens`: when executed, all the selected tokens will be revoked. Please note this option is shown only, if there is no KnoxSSO cookie (token) selected (i.e. batch revocation only works with regular tokens).

**Toggles**

- `Show Disabled KnoxSSO Cookies`: this is true by default. Since disabled KnoxSSO cookies remain in the underlying token state service until they expire, it may bother users to see them in the tokens table. Flipping this toggle button helps to hide them.
- `Show My Tokens Only`: this toggle button is only visible to users, who can see all tokens. By default, this is false. Enabling it will filter the tokens table in a way such that it will contain tokens only that were generated for the logged in user (impersonated or not).

**Configuration**

By default, logged in users can see token that were generated by them or for them (in caase of token impersonation). However, you may want to edit the `gateway.knox.token.management.users.can.see.all.tokens` parameter in `gateway-site.xml` to allow other users than `admin` to become such a "superuser", who can see all tokens on the Token Management UI.

     <property>
        <name>gateway.knox.token.management.users.can.see.all.tokens</name>
        <value>admin</value>
        <description>A comma-separated list of user names who can see all tokens on the Token Management page</description>
    </property>

##### Token impersonation

On the token generation page end-users can generate tokens on behalf of other users by specifying the desired user name in the token `impersonation` field. The following screenshot sows a successful token generation for user `tom` (the logged in user is `admin`).

 ![Successful Token Generation with DoAs](assets/images/token/knoxtokenmanagement_token_generation_ui-successful-doas.png)
 
For this to work, the topology has to be configured with
the HadoopAuth authentication provider, or
an identity assertion provider where impersonation is enabled
In both cases, `doAs` support will only work with a valid Hadoop proxyuser configuration (see [Hadoop Proxyuser impersonation](#hadoop-proxyuser-impersonation) above)

##### Token metadata

As indicated above, the `KNOXTOKEN` service maintains some hard-coded token metadata out-of-the-box:

* userName
* comment
* enabled
* passcode
* createdBy (in case of impersonated tokens)

In v2.0.0, the Knox team implemented a change in this service that allows end-users to add accept query parameters starting with the `md_` prefix and treat them as Knox Token Metadata.

For instance:

`curl -iku admin:admin-password -X GET 'https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token?md_notebookName=accountantKnoxToken&md_souldBeRemovedBy=31March2022&md_otherMeaningfuMetadata=KnoxIsCool'`
When such a token is created by Knox, we should save the following metadata too:

*  notebookName=accountantKnoxToken
*  shouldBeRemovedBy=31March2022
*  otherMeaningfulMetadata=KnoxIsCool

It's not only Knox can save these metadata, but the Knox's existing `getUserTokens API` endpoint is able to fetch basic token information using the supplied metadata name besides the user name information.

It's important to note the following: the `getUserTokens` API returns tokens if *any* of the supplied metadata exists for the given token. Metadata values may or may not be matched: you can either use the `*` wildcard to match all metadata values with a given name *or* you can further filter the stored metadata information by specifying the desired value.

For instance:

`curl -iku admin:admin-password -X GET 'https://localhost:8443/gateway/sandbox/knoxtoken/api/v1/token/getUserTokens?userName=admin&md_notebookName=accountantKnoxToken&md_name=*'`

will return all Knox tokens where metadata with `notebookName` exists and equals `accountantKnoxToken` OR metadata with `name` exists.


Another sample:

1. Create token1 with `md_Name=reina&md_Score=50`
2. Create token2 with `md_Name=mary&md_Score=100`
3. Create token3 with `md_Name=mary&md_Score=20&md_Grade=A`

The following table shows the returned token(s) in case metadata filtering is added in the `getUserTokens` API:

| Metadata | Token returned |
|--|--|
|md_Name=reina|token1|
|md_Name=mary|token2 and token3|
|md_Score=100|token2|
|md_Name=mary&md_Score=20|token2 and token3|
|md_Name=mary&md_Name=reina|token1, token2 and token3|
|md_Name=*|token1, token2 and token3|
|md_Uknown=*|Empty list|

You may want to check out [GitHub Pull Request #542](https://github.com/apache/knox/pull/542) for sample `curl` commands.
