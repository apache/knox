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

### Pac4j Provider - CAS / OAuth / SAML / OpenID Connect ###

<p align="center">
  <img src="https://www.pac4j.org/img/logo-knox.png" width="300" />
</p>

[pac4j](https://github.com/pac4j/pac4j) is a Java security engine to authenticate users, get their profiles and manage their authorizations in order to secure Java web applications.

It supports many authentication mechanisms for UI and web services and is implemented by many frameworks and tools.

For Knox, it is used as a federation provider to support the OAuth, CAS, SAML and OpenID Connect protocols. It must be used for SSO, in association with the KnoxSSO service and optionally with the SSOCookieProvider for access to REST APIs.


#### Configuration ####
##### SSO topology #####

To enable SSO for REST API access through the Knox gateway, you need to protect your Hadoop services with the SSOCookieProvider configured to use the KnoxSSO service (sandbox.xml topology):

    <gateway>
      <provider>
        <role>federation</role>
        <name>SSOCookieProvider</name>
        <enabled>true</enabled>
        <param>
          <name>sso.authentication.provider.url</name>
          <value>https://127.0.0.1:8443/gateway/knoxsso/api/v1/websso</value>
        </param>
      </provider>
      <provider>
        <role>identity-assertion</role>
        <name>Default</name>
        <enabled>true</enabled>
      </provider>
    </gateway>

    <service>
      <role>NAMENODE</role>
      <url>hdfs://localhost:8020</url>
    </service>

    ...

and protect the KnoxSSO service by the pac4j provider (knoxsso.xml topology):

    <gateway>
      <provider>
        <role>federation</role>
        <name>pac4j</name>
        <enabled>true</enabled>
        <param>
          <name>pac4j.callbackUrl</name>
          <value>https://127.0.0.1:8443/gateway/knoxsso/api/v1/websso</value>
        </param>
        <param>
          <name>cas.loginUrl</name>
          <value>https://casserverpac4j.herokuapp.com/login</value>
        </param>
      </provider>
      <provider>
        <role>identity-assertion</role>
        <name>Default</name>
        <enabled>true</enabled>
      </provider>
    </gateway>
    
    <service>
      <role>KNOXSSO</role>
      <param>
        <name>knoxsso.cookie.secure.only</name>
        <value>true</value>
      </param>
      <param>
        <name>knoxsso.token.ttl</name>
        <value>100000</value>
      </param>
      <param>
         <name>knoxsso.redirect.whitelist.regex</name>
         <value>^https?:\/\/(localhost|127\.0\.0\.1|0:0:0:0:0:0:0:1|::1):[0-9].*$</value>
      </param>
    </service>

Notice that the pac4j callback URL is the KnoxSSO URL (`pac4j.callbackUrl` parameter). An additional `pac4j.cookie.domain.suffix` parameter allows you to define the domain suffix for the pac4j cookies.

In this example, the pac4j provider is configured to authenticate users via a CAS server hosted at: https://casserverpac4j.herokuapp.com/login.

##### Parameters #####

You can define the identity provider client/s to be used for authentication with the appropriate parameters - as defined below.
When configuring any pac4j identity provider client there is a mandatory parameter that must be defined to indicate the order in which the providers should be engaged with the first in the comma separated list being the default. Consuming applications may indicate their desire to use one of the configured clients with a query parameter called client_name. When there is no client_name specified, the default (first) provider is selected.

    <param>
      <name>clientName</name>
      <value>CLIENTNAME[,CLIENTNAME]</value>
    </param>

Valid client names are: `FacebookClient`, `TwitterClient`, `CasClient`, `SAML2Client` or `OidcClient`

For tests only, you can use a basic authentication where login equals password by defining the following configuration:

    <param>
      <name>clientName</name>
      <value>testBasicAuth</value>
    </param>

NOTE: This is NOT a secure mechanism and must NOT be used in production deployments.

By default Knox will accept the subject of the returned UserProfile and pass it as the PrimaryPrincipal to the proxied service. If you want to use a different user attribute, you can set the UserProfile attribute name as configuration parameter called pac4j.id_attribute.

    <param>
      <name>pac4j.id_attribute</name>
      <value>nickname</value>
    </param>

Otherwise, you can use Facebook, Twitter, a CAS server, a SAML IdP or an OpenID Connect provider by using the following parameters:

##### For OAuth support:

Name | Value
-----|------
facebook.id     | Identifier of the OAuth Facebook application
facebook.secret | Secret of the OAuth Facebook application
facebook.scope  | Requested scope at Facebook login
facebook.fields | Fields returned by Facebook
twitter.id      | Identifier of the OAuth Twitter application
twitter.secret  | Secret of the OAuth Twitter application

##### For CAS support:

Name | Value
-----|------
cas.loginUrl | Login URL of the CAS server
cas.protocol | CAS protocol (`CAS10`, `CAS20`, `CAS20_PROXY`, `CAS30`, `CAS30_PROXY`, `SAML`)

##### For SAML support:

Name | Value
-----|------
saml.keystorePassword              | Password of the keystore (storepass)
saml.privateKeyPassword            | Password for the private key (keypass)
saml.keystorePath                  | Path of the keystore
saml.identityProviderMetadataPath  | Path of the identity provider metadata
saml.maximumAuthenticationLifetime | Maximum lifetime for authentication
saml.serviceProviderEntityId       | Identifier of the service provider
saml.serviceProviderMetadataPath   | Path of the service provider metadata

> Get more details on the [pac4j wiki](https://github.com/pac4j/pac4j/wiki/Clients#saml-support).

The SSO URL in your SAML 2 provider config will need to include a special query parameter that lets the pac4j provider know that the request is coming back from the provider rather than from a redirect from a KnoxSSO participating application. This query parameter is "pac4jCallback=true".

This results in a URL that looks something like:

    https://hostname:8443/gateway/knoxsso/api/v1/websso?pac4jCallback=true&client_name=SAML2Client

This also means that the SP Entity ID should also include this query parameter as appropriate for your provider.
Often something like the above URL is used for both the SSO URL and SP Entity ID.

##### For OpenID Connect support:

Name | Value
-----|------
oidc.id                    | Identifier of the OpenID Connect provider
oidc.secret                | Secret of the OpenID Connect provider
oidc.discoveryUri          | Direcovery URI of the OpenID Connect provider
oidc.useNonce              | Whether to use nonce during login process
oidc.preferredJwsAlgorithm | Preferred JWS algorithm
oidc.maxClockSkew          | Max clock skew during login process
oidc.customParamKey1       | Key of the first custom parameter
oidc.customParamValue1     | Value of the first custom parameter
oidc.customParamKey2       | Key of the second custom parameter
oidc.customParamValue2     | Value of the second custom parameter

> Get more details on the [pac4j wiki](https://github.com/pac4j/pac4j/wiki/Clients#openid-connect-support).

In fact, you can even define several identity providers at the same time, the first being chosen by default unless you define a `client_name` parameter to specify it (`FacebookClient`, `TwitterClient`, `CasClient`, `SAML2Client` or `OidcClient`).

##### UI invocation

In a browser, when calling your Hadoop service (for example: `https://127.0.0.1:8443/gateway/sandbox/webhdfs/v1/tmp?op=LISTSTATUS`), you are redirected to the identity provider for login. Then, after a successful authentication, your are redirected back to your originally requested URL and your KnoxSSO session is initialized.
