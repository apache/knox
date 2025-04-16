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

### Authentication ###

There are two types of providers supported in Knox for establishing a user's identity:

1. Authentication Providers
2. Federation Providers

Authentication providers directly accept a user's credentials and validates them against some particular user store. Federation providers, on the other hand, validate a token that has been issued for the user by a trusted Identity Provider (IdP).

The current release of Knox ships with an authentication provider based on the Apache Shiro project and is initially configured for BASIC authentication against an LDAP store. This has been specifically tested against Apache Directory Server and Active Directory.

This section will cover the general approach to leveraging Shiro within the bundled provider including:

1. General mapping of provider config to `shiro.ini` config
2. Specific configuration for the bundled BASIC/LDAP configuration
3. Some tips into what may need to be customized for your environment
4. How to setup the use of LDAP over SSL or LDAPS

#### General Configuration for Shiro Provider ####

As is described in the configuration section of this document, providers have a name-value based configuration - as is the common pattern in the rest of Hadoop.

The following example shows the format of the configuration for a given provider:

    <provider>
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
        <param>
            <name>{name}</name>
            <value>{value}</value>
        </param>
    </provider>

Conversely, the Shiro provider currently expects a `shiro.ini` file in the `WEB-INF` directory of the cluster specific web application.

The following example illustrates a configuration of the bundled BASIC/LDAP authentication config in a `shiro.ini` file:

    [urls]
    /**=authcBasic
    [main]
    ldapRealm=org.apache.shiro.realm.ldap.JndiLdapRealm
    ldapRealm.contextFactory.authenticationMechanism=simple
    ldapRealm.contextFactory.url=ldap://localhost:33389
    ldapRealm.userDnTemplate=uid={0},ou=people,dc=hadoop,dc=apache,dc=org

In order to fit into the context of an INI file format, at deployment time we interrogate the parameters provided in the provider configuration and parse the INI section out of the parameter names. The following provider config illustrates this approach. Notice that the section names in the above shiro.ini match the beginning of the parameter names that are in the following config:

    <gateway>
        <provider>
            <role>authentication</role>
            <name>ShiroProvider</name>
            <enabled>true</enabled>
            <param>
                <name>main.ldapRealm</name>
                <value>org.apache.shiro.realm.ldap.JndiLdapRealm</value>
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
                <name>main.ldapRealm.contextFactory.authenticationMechanism</name>
                <value>simple</value>
            </param>
            <param>
                <name>urls./**</name>
                <value>authcBasic</value>
            </param>
        </provider>

This happens to be the way that we are currently configuring Shiro for BASIC/LDAP authentication. This same config approach may be used to achieve other authentication mechanisms or variations on this one. We however have not tested additional uses for it for this release.

#### LDAP Configuration ####

This section discusses the LDAP configuration used above for the Shiro Provider. Some of these configuration elements will need to be customized to reflect your deployment environment.

**main.ldapRealm** - this element indicates the fully qualified class name of the Shiro realm to be used in authenticating the user. The class name provided by default in the sample is the `org.apache.shiro.realm.ldap.JndiLdapRealm` this implementation provides us with the ability to authenticate but by default has authorization disabled. In order to provide authorization - which is seen by Shiro as dependent on an LDAP schema that is specific to each organization - an extension of JndiLdapRealm is generally used to override and implement the doGetAuthorizationInfo method. In this particular release we are providing a simple authorization provider that can be used along with the Shiro authentication provider.

**main.ldapRealm.userDnTemplate** - in order to bind a simple username to an LDAP server that generally requires a full distinguished name (DN), we must provide the template into which the simple username will be inserted. This template allows for the creation of a DN by injecting the simple username into the common name (CN) portion of the DN. **This element will need to be customized to reflect your deployment environment.** The template provided in the sample is only an example and is valid only within the LDAP schema distributed with Knox and is represented by the `users.ldif` file in the `{GATEWAY_HOME}/conf` directory.

**main.ldapRealm.contextFactory.url** - this element is the URL that represents the host and port of the LDAP server. It also includes the scheme of the protocol to use. This may be either `ldap` or `ldaps` depending on whether you are communicating with the LDAP over SSL (highly recommended). **This element will need to be customized to reflect your deployment environment.**.

**main.ldapRealm.contextFactory.authenticationMechanism** - this element indicates the type of authentication that should be performed against the LDAP server. The current default value is `simple` which indicates a simple bind operation. This element should not need to be modified and no mechanism other than a simple bind has been tested for this particular release.

**urls./**** - this element represents a single URL_Ant_Path_Expression and the value the Shiro filter chain to apply to it. This particular sample indicates that all paths into the application have the same Shiro filter chain applied. The paths are relative to the application context path. The use of the value `authcBasic` here indicates that BASIC authentication is expected for every path into the application. Adding an additional Shiro filter to that chain for validating that the request isSecure() and over SSL can be achieved by changing the value to `ssl, authcBasic`. This parameter can be used to exclude endpoints from authentication, this is important in case of jwks endpoints which need not require authentication. We have support for unauthenticated paths in other authenitcation providers and this support can be extended here using the `urls` parameter. Following is an example of how `/knoxtoken/api/v1/jwks.json` endpoint can be excluded from authentication in shiro configuration.

        <param>
            <name>urls./knoxtoken/api/v1/jwks.json</name>
            <value>anon</value>
        </param>

#### Active Directory - Special Note ####

You would use LDAP configuration as documented above to authenticate against Active Directory as well.

Some Active Directory specific things to keep in mind:

Typical AD `main.ldapRealm.userDnTemplate` value looks slightly different, such as

    cn={0},cn=users,DC=lab,DC=sample,dc=com

Please compare this with a typical Apache DS `main.ldapRealm.userDnTemplate` value and make note of the difference:

    `uid={0},ou=people,dc=hadoop,dc=apache,dc=org`

If your AD is configured to authenticate based on just the cn and password and does not require user DN, you do not have to specify value for `main.ldapRealm.userDnTemplate`.


#### LDAP over SSL (LDAPS) Configuration ####
In order to communicate with your LDAP server over SSL (again, highly recommended), you will need to modify the topology file in a couple ways and possibly provision some keying material.

1. **main.ldapRealm.contextFactory.url** must be changed to have the `ldaps` protocol scheme and the port must be the SSL listener port on your LDAP server.
2. Identity certificate (keypair) provisioned to LDAP server - your LDAP server specific documentation should indicate what is required for providing a cert or keypair to represent the LDAP server identity to connecting clients.
3. Trusting the LDAP Server's public key - if the LDAP Server's identity certificate is issued by a well known and trusted certificate authority and is already represented in the JRE's cacerts truststore then you don't need to do anything for trusting the LDAP server's cert. If, however, the cert is self-signed or issued by an untrusted authority you will need to either add it to the cacerts keystore or to another truststore that you may direct Knox to utilize through a system property (`javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`).

#### Session Configuration ####

Knox maps each cluster topology to a web application and leverages standard JavaEE session management.

To configure session idle timeout for the topology, please specify value of parameter sessionTimeout for ShiroProvider in your topology file. If you do not specify the value for this parameter, it defaults to 30 minutes.

The definition would look like the following in the topology file:

    ...
    <provider>
        <role>authentication</role>
        <name>ShiroProvider</name>
        <enabled>true</enabled>
        <param>
            <!--
            Session timeout in minutes. This is really idle timeout.
            Defaults to 30 minutes, if the property value is not defined.
            Current client authentication will expire if client idles
            continuously for more than this value
            -->
            <name>sessionTimeout</name>
            <value>30</value>
        </param>
    <provider>
    ...

At present, ShiroProvider in Knox leverages JavaEE session to maintain authentication state for a user across requests using JSESSIONID cookie. So, a client that authenticated with Knox could pass the JSESSIONID cookie with repeated requests as long as the session has not timed out instead of submitting userid/password with every request. Presenting a valid session cookie in place of userid/password would also perform better as additional credential store lookups are avoided.
