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
-->
## KnoxSSO Setup and Configuration

### Introduction
---

Authentication of the Hadoop component UIs, and those of the overall ecosystem, is usually limited to Kerberos (which requires SPNEGO to be configured for the user's browser) and simple/pseudo. This often results in the UIs not being secured - even in secured clusters. This is where KnoxSSO provides value by providing WebSSO capabilities to the Hadoop cluster.

By leveraging the hadoop-auth module in Hadoop common, we have introduced the ability to consume a common SSO cookie for web UIs while retaining the non-web browser authentication through kerberos/SPNEGO. We do this by extending the AltKerberosAuthenticationHandler class which provides the useragent based multiplexing. 

We also provide integration guidance within the developers guide for other applications to be able to participate in these SSO capabilities.

The flexibility of the Apache Knox authentication and federation providers allows KnoxSSO to provide a normalization of authentication events through token exchange resulting in a common JWT (JSON WebToken) based token.

KnoxSSO provides an abstraction for integrating any number of authentication systems and SSO solutions and enables participating web applications to scale to those solutions more easily. Without the token exchange capabilities offered by KnoxSSO each component UI would need to integrate with each desired solution on its own. With KnoxSSO they only need to integrate with the single solution and common token.

In addition, KnoxSSO comes with its own form-based IdP. This allows for easily integrating a form-based login with the enterprise AD/LDAP server.

This document describes the overall setup requirements for KnoxSSO and participating applications.

In v2.0.0, the Knox team implemented an extension to the KnoxSSO feature that controls the number of concurrent UI sessions the users can have. For more informstion please check out the [Concurrent Session Verification](#Concurrent+Session+Verification) section below.

### Form-based IdP Setup
By default the `knoxsso.xml` topology contains an application element for the knoxauth login application. This is a simple single page application for providing a login page and authenticating the user with HTTP basic auth against AD/LDAP.

    <application>
        <name>knoxauth</name>
    </application>

The Shiro Provider has specialized configuration beyond the typical HTTP Basic authentication requirements for REST APIs or other non-knoxauth applications. You will notice below that there are a couple additional elements - namely, **redirectToUrl** and **restrictedCookies with WWW-Authenticate**. These are used to short-circuit the browser's HTTP basic dialog challenge so that we can use a form instead.

    <provider>
       <role>authentication</role>
       <name>ShiroProvider</name>
       <enabled>true</enabled>
       <param>
          <name>sessionTimeout</name>
          <value>30</value>
       </param>
       <param>
          <name>redirectToUrl</name>
          <value>/gateway/knoxsso/knoxauth/login.html</value>
       </param>
       <param>
          <name>restrictedCookies</name>
          <value>rememberme,WWW-Authenticate</value>
       </param>
       <param>
          <name>main.ldapRealm</name>
          <value>org.apache.knox.gateway.shirorealm.KnoxLdapRealm</value>
       </param>
       <param>
          <name>main.ldapContextFactory</name>
          <value>org.apache.knox.gateway.shirorealm.KnoxLdapContextFactory</value>
       </param>
       <param>
          <name>main.ldapRealm.contextFactory</name>
          <value>$ldapContextFactory</value>
       </param>
       <param>
          <name>main.ldapRealm.userDnTemplate</name>
          <value>uid={0},ou=people,dc=hadoop,dc=apache,dc=org</value>
       </param>
       <param>
          <name>main.ldapRealm.contextFactory.url</name>
          <value>ldap://localhost:33389</value>
       </param>
       <param>
          <name>main.ldapRealm.authenticationCachingEnabled</name>
          <value>false</value>
       </param>
       <param>
          <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
          <value>simple</value>
       </param>
       <param>
          <name>urls./**</name>
          <value>authcBasic</value>
       </param>
    </provider>

### KnoxSSO Service Setup

#### knoxsso.xml Topology
To enable KnoxSSO, we use the KnoxSSO topology for exposing an API that can be used to abstract the use of any number of enterprise or customer IdPs. By default, the `knoxsso.xml` file is configured for using the simple KnoxAuth application for form-based authentication against LDAP/AD. By swapping the Shiro authentication provider that is there out-of-the-box with another authentication or federation provider, an admin may leverage many of the existing providers for SSO for the UI components that participate in KnoxSSO.

Just as with any Knox service, the KNOXSSO service is protected by the gateway providers defined above it. In this case, the ShiroProvider is taking care of HTTP Basic Auth against LDAP for us. Once the user authenticates the request processing continues to the KNOXSSO service that will create the required cookie and do the necessary redirects.

The knoxsso.xml topology will result in a KnoxSSO URL that looks something like:

    https://{gateway_host}:{gateway_port}/gateway/knoxsso/api/v1/websso

This URL is needed when configuring applications that participate in KnoxSSO for a given deployment. We will refer to this as the Provider URL.

#### KnoxSSO Configuration Parameters

Parameter                        | Description | Default
-------------------------------- |------------ |----------- 
knoxsso.cookie.name              | This optional setting allows the admin to set the name of the sso cookie to use to represent a successful authentication event. | hadoop-jwt
knoxsso.cookie.secure.only       | This determines whether the browser is allowed to send the cookie over unsecured channels. This should always be set to true in production systems. If during development a relying party is not running SSL then you can turn this off. Running with it off exposes the cookie and underlying token for capture and replay by others. | The value of the gateway-site property named `ssl.enabled` (which defaults to `true`).
knoxsso.cookie.max.age           | optional: This indicates that a cookie can only live for a specified amount of time - in seconds. This should probably be left to the default which makes it a session cookie. Session cookies are discarded once the browser session is closed. | session
knoxsso.cookie.domain.suffix     | optional: This indicates the portion of the request hostname that represents the domain to be used for the cookie domain. For single host development scenarios, the default behavior should be fine. For production deployments, the expected domain should be set and all configured URLs that are related to SSO should use this domain. Otherwise, the cookie will not be presented by the browser to mismatched URLs. | Default cookie domain or a domain derived from a hostname that includes more than 2 dots.
knoxsso.token.ttl                | This indicates the lifespan of the token within the cookie. Once it expires a new cookie must be acquired from KnoxSSO. This is in milliseconds. The 36000000 in the topology above gives you 10 hrs. | 30000 That is 30 seconds.
knoxsso.token.audiences          | This is a comma separated list of audiences to add to the JWT token. This is used to ensure that a token received by a participating application knows that the token was intended for use with that application. It is optional. In the event that an application has expected audiences and they are not present the token must be rejected. In the event where the token has audiences and the application has none expected then the token is accepted.| empty
knoxsso.redirect.whitelist.regex | A semicolon-delimited list of regular expressions. The incoming originalUrl must match one of the expressions in order for KnoxSSO to redirect to it after authentication. Note that cookie use is still constrained to redirect destinations in the same domain as the KnoxSSO service - regardless of the expressions specified here. | The value of the gateway-site property named *gateway.dispatch.whitelist*. If that is not defined, the default allows only relative paths, localhost or destinations in the same domain as the Knox host (with or without SSL). This may need to be opened up for production use and actual participating applications.
knoxsso.expected.params          | Optional: Comma separated list of query parameters that are expected and consumed by KnoxSSO and will not be passed on to originalUrl | empty
knoxsso.signingkey.keystore.name | Optional: name of a JKS keystore in gateway security directory that has required signing key certificate | empty
knoxsso.signingkey.keystore.alias | Optional: alias of the signing key certificate in the `knoxsso.signingkey.keystore.name` keystore | empty
knoxsso.signingkey.keystore.passphrase.alias | Optional: passphrase alias for the signing key certificate | empty

### Participating Application Configuration
#### Hadoop Configuration Example
The following is used as the KnoxSSO configuration in the Hadoop JWTRedirectAuthenticationHandler implementation. Any participating application will need similar configuration. Since JWTRedirectAuthenticationHandler extends the AltKerberosAuthenticationHandler, the typical Kerberos configuration parameters for authentication are also required.


    <property>
        <name>hadoop.http.authentication.type</name
        <value>org.apache.hadoop.security.authentication.server.JWTRedirectAuthenticationHandler</value>
    </property>


This is the handler classname in Hadoop auth for JWT token (KnoxSSO) support.


    <property>
        <name>hadoop.http.authentication.authentication.provider.url</name>
        <value>https://c6401.ambari.apache.org:8443/gateway/knoxsso/api/v1/websso</value>
    </property>


The above property is the SSO provider URL that points to the knoxsso endpoint.

    <property>
        <name>hadoop.http.authentication.public.key.pem</name>
        <value>MIICVjCCAb+gAwIBAgIJAPPvOtuTxFeiMA0GCSqGSIb3DQEBBQUAMG0xCzAJBgNV
      BAYTAlVTMQ0wCwYDVQQIEwRUZXN0MQ0wCwYDVQQHEwRUZXN0MQ8wDQYDVQQKEwZI
      YWRvb3AxDTALBgNVBAsTBFRlc3QxIDAeBgNVBAMTF2M2NDAxLmFtYmFyaS5hcGFj
      aGUub3JnMB4XDTE1MDcxNjE4NDcyM1oXDTE2MDcxNTE4NDcyM1owbTELMAkGA1UE
      BhMCVVMxDTALBgNVBAgTBFRlc3QxDTALBgNVBAcTBFRlc3QxDzANBgNVBAoTBkhh
      ZG9vcDENMAsGA1UECxMEVGVzdDEgMB4GA1UEAxMXYzY0MDEuYW1iYXJpLmFwYWNo
      ZS5vcmcwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMFs/rymbiNvg8lDhsdA
      qvh5uHP6iMtfv9IYpDleShjkS1C+IqId6bwGIEO8yhIS5BnfUR/fcnHi2ZNrXX7x
      QUtQe7M9tDIKu48w//InnZ6VpAqjGShWxcSzR6UB/YoGe5ytHS6MrXaormfBg3VW
      tDoy2MS83W8pweS6p5JnK7S5AgMBAAEwDQYJKoZIhvcNAQEFBQADgYEANyVg6EzE
      2q84gq7wQfLt9t047nYFkxcRfzhNVL3LB8p6IkM4RUrzWq4kLA+z+bpY2OdpkTOe
      wUpEdVKzOQd4V7vRxpdANxtbG/XXrJAAcY/S+eMy1eDK73cmaVPnxPUGWmMnQXUi
      TLab+w8tBQhNbq6BOQ42aOrLxA8k/M4cV1A=</value>
    </property>

The above property holds the KnoxSSO server's public key for signature verification. Adding it directly to the config like this is convenient and is easily done through Ambari to existing config files that take custom properties. Config is generally protected as root access only as well - so it is a pretty good solution.

Individual UIs within the Hadoop ecosystem will have similar configuration for participating in the KnoxSSO websso capabilities.

Blogs will be provided on the Apache Knox project site for these usecases as they become available.

### KnoxSSO Cookie Invalidation

This feature was implemented in the scope of [KNOX-2691](https://issues.apache.org/jira/browse/KNOX-2691).

The user story is that there is a need for a new feature that would allow a pre-configured superuser to invalidate previously issued Knox SSO tokens for (a) particular user(s) in case there is a malicious attack in terms of one (or more) of those users' SSO tokens got compromised.

To be able to achieve this goal, the `KNOXSSO` service is modified in a way such that it saves the generated SSO cookie using Knox's token state service capabilities in case token management is enabled in KNOXSSO's configuration (using the well-known `knox.token.exp.server-managed=true` parameter, by default this is set to `false` in the relevant topologies).

This is only the SSO cookie generation side of the feature. The verification side also needs to be configured the same way: the `SSOCookieProvider` configuration must have the same parameter to enable this new feature.

It is very important to highlight, that turning this feature on will make previously initiated `KNOX SSO sessions` invalid, therefore the browsers must be closed, and/or the cookies have to be removed. This will ensure new user logins which will be captured by the enabled token state service.

There is another essential configuration when `KNOXSSO` is configured to use the [Pac4J federation filter](#Pac4j+Provider+-+CAS+/+OAuth+/+SAML+/+OpenID+Connect). In this case, the `knox.global.logout.page.url` configuration is a must-have parameter in `gateway-site.xml` which usually points to the logout endpoint of the pre-configured SAML/OIDC callback.

Together with the new [Token Management UI](#Token+Management), pre-configured "superusers" can disable (invalidate) SSO cookies. This will result in forcing the users to log in again, which, for obvious reasons, the malicious user(s) cannot do.

### Concurrent Session Verification

#### Overview

This feature allows end-users limiting the number of concurrent UI sessions the users can have.  In order to reach this goal the users can be sorted out into three groups: non-privileged, privileged, unlimited.

The non-privileged and privileged groups each have a configurable limit, which the members of the group can not exceed. The members of the unlimited group are able to create unlimited 
number of concurrent sessions.

All of the users, who are not configured in neither the privileged nor in the unlimited group, shall become automatically the member of the non-privileged group.

#### Configuration

The following table shows the relevant gateway-level parameters that are essential for this feature to work. Please note these parameters are not listed above in the Gateway Server Configuration table.

Parameter                        | Description | Default
-------------------------------- |------------ |-----------
gateway.service.concurrentsessionverifier.impl | To enable the session verification feature, end-users should set this parameter to `org.apache.knox.gateway.session.control.InMemoryConcurrentSessionVerifier` | `org.apache.knox.gateway.session.control.EmptyConcurrentSessionVerifier`
gateway.session.verification.privileged.users | Indicates a list of users that are qualified 'privileged'. | Empty list 
gateway.session.verification.unlimited.users | Indicates a list of (super) users that can have as many UI sessions as they want. | Empty list
gateway.session.verification.privileged.user.limit | The number of UI sessions a 'privileged' user can have | 3
gateway.session.verification.non.privileged.user.limit | The number of UI sessions a 'non-privileged' user can have | 2
gateway.session.verification.expired.tokens.cleaning.period | The time period (seconds) about the expired session verification data is removed from the background storage | 1800


#### How this works

If the verifier is disabled it will not do anything even if the other parameters are configured.

When the verifier is enabled all of the users are considered as a non-privileged user by default and they will not be able to create more 
concurrent sessions than the non-privileged limit. The same is true after you added someone in the privileged user group: that user will not be able to create more UI sessions than the configured privileged user limit. Whereas the members of the unlimited users group are able to create unlimited number of concurrent sessions even if they are configured in the privileged group as well.

The underlying verifier stores verification data (included JWTs) provided by KnoxSSO and used for login. In order to save resources the expired tokens are deleted periodically. The cleaning period can also be updated thru the `gateway.session.verification.expired.tokens.cleaning.period` parameter.

