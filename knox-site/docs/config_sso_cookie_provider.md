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

### SSO Cookie Provider ###

#### Overview ####
The SSOCookieProvider enables the federation of the authentication event that occurred through KnoxSSO. KnoxSSO is a typical SP initiated websso mechanism that sets a cookie to be presented by browsers to participating applications and cryptographically verified.

Knox Gateway needs a pluggable mechanism for consuming these cookies and federating the KnoxSSO authentication event as an asserted identity in its interaction with the Hadoop cluster for REST API invocations. This provider is useful when an application that is integrated with KnoxSSO for authentication also consumes REST APIs through the Knox Gateway.

Based on our understanding of the WebSSO flow it should behave like:

* SSOCookieProvider checks for hadoop-jwt cookie and in its absence redirects to the configured SSO provider URL (knoxsso endpoint)
* The configured Provider on the KnoxSSO endpoint challenges the user in a provider specific way (presents form, redirects to SAML IdP, etc.)
* The authentication provider on KnoxSSO validates the identity of the user through credentials/tokens
* The WebSSO service exchanges the normalized Java Subject into a JWT token and sets it on the response as a cookie named `hadoop-jwt`
* The WebSSO service then redirects the user agent back to the originally requested URL - the requested Knox service subsequent invocations will find the cookie in the incoming request and not need to engage the WebSSO service again until it expires.

#### Configuration ####
##### sandbox.json Topology Example
Configuring one of the cluster topologies to use the SSOCookieProvider instead of the out of the box ShiroProvider would look something like the following:

sso-provider.json

    {
      "providers": [
        {
          "role": "federation",
          "name": "SSOCookieProvider",
          "enabled": "true",
          "params": {
            "sso.authentication.provider.url": "https://localhost:9443/gateway/idp/api/v1/websso"
          }
        }
      ]
    }

sandbox.json

    {
      "provider-config-ref": "sso-provider",
      "services": [
        {
          "name": "WEBHDFS",
          "urls": [
            "http://localhost:50070/webhdfs"
          ]
        },
        {
          "name": "WEBHCAT",
          "urls": [
            "http://localhost:50111/templeton"
          ]
        }
      ]
    }

The following table describes the configuration options for the sso cookie provider:

##### Descriptions #####

Name | Description | Default
---------|-----------|---------
sso.authentication.provider.url | Required parameter that indicates the location of the KnoxSSO endpoint and where to redirect the useragent when no SSO cookie is found in the incoming request. | N/A
sso.token.verification.pem | Optional parameter that specifies public key used to validate hadoop-jwt token. The key must be in PEM encoded format excluding the header and footer lines.| N/A
sso.expected.audiences | Optional parameter used to constrain the use of tokens on this endpoint to those that have tokens with at least one of the configured audience claims. | N/A
sso.unauthenticated.path.list | Optional - List of paths that should bypass the SSO flow. | favicon.ico

### JWT Provider ###

#### Overview ####
The JWT federation provider accepts JWT tokens as Bearer tokens within the Authorization header of the incoming request. Upon successfully extracting and verifying the token, the request is then processed on behalf of the user represented by the JWT token.

This provider is closely related to the [Knox Token Service](#KnoxToken+Configuration) and is essentially the provider that is used to consume the tokens issued by the [Knox Token Service](#KnoxToken+Configuration).

Typical deployments have the KnoxToken service defined in a topology that authenticates users based on username and password with the ShiroProvider. They also have another topology dedicated to clients that wish to use KnoxTokens to access Hadoop resources through Knox. 
The following provider configuration can be used with such a topology.

    "providers": [
      {
        "role": "federation",
        "name": "JWTProvider",
        "enabled": "true",
        "params": {
		  "knox.token.audiences": "tokenbased"
        }
      }
    ]

The `knox.token.audiences` parameter above indicates that any token in an incoming request must contain an audience claim called "tokenbased". In this case, the idea is that the issuing KnoxToken service will be configured to include such an audience claim and that the resulting token is valid to use in the topology that contains configuration like above. This would generally be the name of the topology but you can standardize on anything.

The following table describes the configuration options for the JWT federation provider:

##### Descriptions #####

Name | Description | Default
---------|-----------|--------
knox.token.audiences | Optional parameter. This parameter allows the administrator to constrain the use of tokens on this endpoint to those that have tokens with at least one of the configured audience claims. These claims have associated configuration within the KnoxToken service as well. This provides an interesting way to make sure that the token issued based on authentication to a particular LDAP server or other IdP is accepted but not others.|N/A
knox.token.exp.server-managed | Optional parameter for specifying that server-managed token state should be referenced for evaluating token validity. | false
knox.token.verification.pem | Optional parameter that specifies public key used to validate the token. The key must be in PEM encoded format excluding the header and footer lines.| N/A
knox.token.use.cookie | Optional parameter that indicates if the JWT token can be retrieved from an HTTP cookie instead of the Authorization header. If this is set to `true`, then Knox will first check if the `hadoop-jwt` cookie (the cookie name is configurable) is available in the request and, if that's the case, Knox will try to fetch a JWT from that cookie. If the cookie is not present in the request, Knox will continue its authentication flow using the Authorization header. If the cookie is there, but it holds an invalid JWT, then authentication will fail. Sample use cases and `curl` commands are available in this [GitHub Pull Request](https://github.com/apache/knox/pull/623). | false
knox.token.cookie.name | Optional parameter to use a custom cookie name in the request if `knox.token.use.cookie = true`. | hadoop-jwt
knox.token.allowed.jws.types | With [KNOX-2149](https://issues.apache.org/jira/browse/KNOX-2149), one can define their own JWKS URL which Knox can use for verification. Previous Knox implementations only supported JWTs with `"typ: JWT"` in their headers (or not type definition at all). In previous JOSE versions, there were other supported types such as `at+jwt` which Knox can support from now on. Please note, this configuration is only applied if token verification goes through the JWKS verification path. | `JWT`


The optional `knox.token.exp.server-managed` parameter indicates that Knox is managing the state of tokens it issues (e.g., expiration) external from the token, and this external state should be referenced when validating tokens. This parameter can be ommitted if the global default is configured in gateway-site (see [gateway.knox.token.exp.server-managed](#Gateway+Server+Configuration)), and matches the requirements of this provider. Otherwise, this provider parameter overrides the gateway configuration for the provider's deployment.

See the [documentation for the Knox Token service](#KnoxToken+Configuration) for related details.
